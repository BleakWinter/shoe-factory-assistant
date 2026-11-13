from threading import Lock

from fastapi import APIRouter, HTTPException, Request, Response

from .delivery_table import DeliveryTableTooLarge, render_delivery_table
from .schemas import DeliveryRenderRequest

router = APIRouter(prefix="/reports/delivery")
render_lock = Lock()


class PngResponse(Response):
    media_type = "image/png"


@router.post("/render", response_class=PngResponse)
def render_delivery_report(report: DeliveryRenderRequest, request: Request) -> PngResponse:
    try:
        with render_lock:
            image = render_delivery_table(report, request.app.state.delivery_fonts)
    except DeliveryTableTooLarge as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    return PngResponse(content=image)
