from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graphs.state import TutorState
from app.llm import LLMProvider
from app.tools import ToolRegistry


class TutorGraph:
    def __init__(self, tools: ToolRegistry, llm: LLMProvider):
        self.tools = tools
        self.llm = llm
        graph = StateGraph(TutorState)
        graph.add_node("tutor_agent", self._route)
        graph.add_node("resource_agent", self._retrieve)
        graph.add_node("quiz_agent", self._quiz)
        graph.add_node("planner_agent", self._plan)
        graph.add_conditional_edges(
            "tutor_agent",
            lambda state: state["intent"],
            {"question": "resource_agent", "quiz": "quiz_agent", "plan": "planner_agent"},
        )
        graph.add_edge(START, "tutor_agent")
        graph.add_edge("resource_agent", END)
        graph.add_edge("quiz_agent", END)
        graph.add_edge("planner_agent", END)
        self.graph = graph.compile()

    async def _route(self, state: TutorState) -> dict[str, Any]:
        message = state["message"]
        intent = "quiz" if any(word in message for word in ("题", "练习", "测试")) else "plan" if any(
            word in message for word in ("计划", "路径", "怎么学")
        ) else "question"
        trace = list(state.get("trace", []))
        trace.append({"type": "agent", "name": "TutorAgent", "intent": intent})
        return {"intent": intent, "trace": trace}

    async def _retrieve(self, state: TutorState) -> dict[str, Any]:
        trace = list(state["trace"])
        contexts = await self.tools.invoke(
            "search_knowledge_base", trace, query=state["message"], top_k=5
        )
        answer = await self.tools.invoke(
            "answer_tutor_question",
            trace,
            question=state["message"],
            contexts=contexts,
        )
        trace.append({"type": "agent", "name": "ResourceAgent"})
        return {"contexts": contexts, "answer": answer, "trace": trace}

    async def _quiz(self, state: TutorState) -> dict[str, Any]:
        trace = list(state["trace"])
        quizzes = await self.tools.invoke(
            "generate_quiz", trace, topic=state["message"], content="", count=3
        )
        trace.append({"type": "agent", "name": "QuizAgent"})
        return {"answer": str(quizzes), "trace": trace}

    async def _plan(self, state: TutorState) -> dict[str, Any]:
        trace = list(state["trace"])
        profile = await self.tools.invoke(
            "retrieve_user_profile",
            trace,
            user_id=state["user_id"],
            supplied=state.get("user_profile"),
        )
        plan = await self.tools.invoke(
            "generate_learning_plan",
            trace,
            topic=state["message"],
            goal="",
            profile=profile,
            resources=[],
        )
        trace.append({"type": "agent", "name": "PlannerAgent"})
        return {"answer": str(plan), "trace": trace}

    async def chat(self, state: TutorState) -> TutorState:
        return await self.graph.ainvoke(state)
