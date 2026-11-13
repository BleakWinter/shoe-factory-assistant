from datetime import date
from typing import Annotated, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Identifier = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=128)]


class DeliveryStyle(BaseModel):
    model_config = ConfigDict(extra="forbid")

    style_no: Identifier = Field(alias="styleNo")
    pending_pairs: int = Field(alias="pendingPairs", strict=True, gt=0)


class DeliveryOrderGroup(BaseModel):
    model_config = ConfigDict(extra="forbid")

    order_no: Identifier = Field(alias="orderNo")
    delivery_date: date | None = Field(alias="deliveryDate")
    overdue_days: int = Field(default=0, alias="overdueDays", strict=True, ge=0)
    styles: list[DeliveryStyle] = Field(min_length=1)

    @model_validator(mode="after")
    def require_date_for_overdue_order(self) -> Self:
        if self.delivery_date is None and self.overdue_days > 0:
            raise ValueError("交期待确认的订单需要将 overdueDays 设为 0")
        return self


class DeliveryRenderRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    report_date: date = Field(alias="reportDate")
    order_groups: list[DeliveryOrderGroup] = Field(alias="orderGroups")
