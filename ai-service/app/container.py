from functools import lru_cache

from app.config import get_settings
from app.graphs import LearningGraph, TutorGraph
from app.llm import create_llm_provider
from app.memory import ProfileClient
from app.rag import RagService
from app.tools import ToolRegistry
from app.tts import GeminiTtsService


class Container:
    def __init__(self):
        self.settings = get_settings()
        self.llm = create_llm_provider(self.settings)
        self.rag = RagService(self.settings)
        self.profiles = ProfileClient(self.settings)
        self.tools = ToolRegistry(self.llm, self.rag, self.profiles)
        self.tts = GeminiTtsService(self.settings)
        self.learning_graph = LearningGraph(self.tools, self.llm, self.profiles)
        self.tutor_graph = TutorGraph(self.tools, self.llm)


@lru_cache
def get_container() -> Container:
    return Container()

