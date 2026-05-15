from dotenv import load_dotenv
import os

load_dotenv()

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-120b")
GROQ_BASE_URL = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1")
MAX_TOKENS = int(os.getenv("MAX_TOKENS", "4096"))


def require_groq_api_key() -> str:
    if not GROQ_API_KEY:
        raise RuntimeError("GROQ_API_KEY is not set in .env")
    return GROQ_API_KEY
