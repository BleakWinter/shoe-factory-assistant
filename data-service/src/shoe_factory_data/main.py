from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .reports.delivery_table import load_delivery_fonts
from .reports.routes import router


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    app.state.delivery_fonts = load_delivery_fonts()
    yield


app = FastAPI(title="鞋厂数据处理服务", lifespan=lifespan)
app.include_router(router)
