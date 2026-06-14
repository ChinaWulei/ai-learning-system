from typing import Any

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph

from app.graphs.state import LearningState
from app.llm import LLMProvider
from app.memory import ProfileClient
from app.tools import ToolRegistry


class LearningGraph:
    def __init__(self, tools: ToolRegistry, llm: LLMProvider, profiles: ProfileClient):
        self.tools = tools
        self.llm = llm
        self.profiles = profiles
        self.checkpointer = InMemorySaver()
        self.plan_graph = self._build_plan_graph()
        self.evaluation_graph = self._build_evaluation_graph()

    def _build_plan_graph(self):
        graph = StateGraph(LearningState)
        graph.add_node("planner_agent", self._planner_agent)
        graph.add_node("resource_agent", self._resource_agent)
        graph.add_node("content_agent", self._content_agent)
        graph.add_node("quiz_agent", self._quiz_agent)
        graph.add_edge(START, "planner_agent")
        graph.add_edge("planner_agent", "resource_agent")
        graph.add_edge("resource_agent", "content_agent")
        graph.add_edge("content_agent", "quiz_agent")
        graph.add_edge("quiz_agent", END)
        return graph.compile(checkpointer=self.checkpointer)

    def _build_evaluation_graph(self):
        graph = StateGraph(LearningState)
        graph.add_node("evaluation_agent", self._evaluation_agent)
        graph.add_node("profile_agent", self._profile_agent)
        graph.add_node("remediation_agent", self._remediation_agent)
        graph.add_edge(START, "evaluation_agent")
        graph.add_edge("evaluation_agent", "profile_agent")
        graph.add_conditional_edges(
            "profile_agent",
            lambda state: state["route"],
            {"remediate": "remediation_agent", "complete": END},
        )
        graph.add_edge("remediation_agent", END)
        return graph.compile(checkpointer=self.checkpointer)

    async def _planner_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state.get("trace", []))
        profile = await self.tools.invoke(
            "retrieve_user_profile",
            trace,
            user_id=state["user_id"],
            supplied=state.get("user_profile"),
        )
        preliminary = await self.tools.invoke(
            "search_knowledge_base", trace, query=state["topic"], top_k=3
        )
        plan = await self.tools.invoke(
            "generate_learning_plan",
            trace,
            topic=state["topic"],
            goal=state.get("goal", ""),
            profile=profile,
            resources=preliminary,
        )
        trace.append({"type": "agent", "name": "PlannerAgent"})
        return {"user_profile": profile, "learning_plan": plan, "trace": trace}

    async def _resource_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state["trace"])
        resources = await self.tools.invoke(
            "search_knowledge_base", trace, query=state["topic"], top_k=5
        )
        trace.append({"type": "agent", "name": "ResourceAgent"})
        return {"resources": resources, "trace": trace}

    async def _content_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state["trace"])
        contexts = state.get("resources", [])
        generated = await self.tools.invoke(
            "generate_learning_content",
            trace,
            topic=state["topic"],
            contexts=contexts,
        )
        content = [generated]
        trace.append({"type": "agent", "name": "ContentAgent"})
        return {"content": content, "trace": trace}

    async def _quiz_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state["trace"])
        text = "\n".join(item["body"] for item in state["content"])
        quizzes = await self.tools.invoke(
            "generate_quiz", trace, topic=state["topic"], content=text, count=3
        )
        trace.append({"type": "agent", "name": "QuizAgent"})
        return {"quizzes": quizzes, "trace": trace}

    async def _evaluation_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state.get("trace", []))
        evaluation = await self.tools.invoke(
            "evaluate_answer",
            trace,
            topic=state["topic"],
            quizzes=state["quizzes"],
            answers=state["answers"],
        )
        trace.append({"type": "agent", "name": "EvaluationAgent"})
        return {"evaluation": evaluation, "trace": trace}

    async def _profile_agent(self, state: LearningState) -> dict[str, Any]:
        profile = dict(state.get("user_profile") or {})
        mastery = dict(profile.get("mastery") or {})
        old_value = float(mastery.get(state["topic"], 0))
        measured = float(state["evaluation"]["mastery"])
        mastery[state["topic"]] = round(old_value * 0.6 + measured * 0.4, 2)
        weak_points = set(profile.get("weak_points") or [])
        if measured < 0.85:
            weak_points.add(state["topic"])
        else:
            weak_points.discard(state["topic"])
        profile.update({"mastery": mastery, "weak_points": sorted(weak_points)})
        await self.profiles.update(state["user_id"], profile)
        route = "complete" if state["evaluation"]["score"] >= 85 else "remediate"
        trace = list(state["trace"])
        trace.append({"type": "agent", "name": "ProfileUpdater", "route": route})
        return {"profile_update": profile, "route": route, "trace": trace}

    async def _remediation_agent(self, state: LearningState) -> dict[str, Any]:
        trace = list(state["trace"])
        resources = await self.tools.invoke(
            "search_knowledge_base",
            trace,
            query=f"{state['topic']} 常见错误 基础",
            top_k=3,
        )
        trace.append({"type": "agent", "name": "PlannerAgent", "mode": "remediation"})
        return {
            "resources": resources,
            "learning_plan": {
                "type": "remediation",
                "objective": f"针对{state['topic']}薄弱点复习",
                "actions": ["复习引用片段", "重做错题", "生成一组同类题"],
            },
            "trace": trace,
        }

    async def generate_plan(self, state: LearningState) -> LearningState:
        config = {"configurable": {"thread_id": f"task-{state['task_id']}-plan"}}
        return await self.plan_graph.ainvoke(state, config=config)

    async def evaluate(self, state: LearningState) -> LearningState:
        config = {"configurable": {"thread_id": f"task-{state['task_id']}-evaluation"}}
        return await self.evaluation_graph.ainvoke(state, config=config)
