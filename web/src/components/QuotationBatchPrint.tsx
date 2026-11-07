import { App } from "antd";
import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import type { Quotation } from "../types/quotation";
import QuotationPreview from "./QuotationPreview";

export default function QuotationBatchPrint({ quotations, onFinish }: { quotations: Quotation[]; onFinish: () => void }) {
  const root = useRef<HTMLDivElement>(null);
  const { message } = App.useApp();
  useEffect(() => {
    let active = true;
    const finish = () => {
      if (!active) return;
      active = false; document.body.classList.remove("quotation-printing"); onFinish();
    };
    window.addEventListener("afterprint", finish);
    document.body.classList.add("quotation-printing");
    async function print() {
      try {
        await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
        if (!active) return;
        const images = Array.from(root.current!.querySelectorAll<HTMLImageElement>("img"));
        await Promise.all(images.map(image => image.decode().catch(() => undefined)));
        await document.fonts.ready;
        await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
        if (active) window.print();
      } catch (error) {
        if (active) { message.error(error instanceof Error ? error.message : "批量打印失败"); finish(); }
      }
    }
    void print();
    return () => { active = false; window.removeEventListener("afterprint", finish); document.body.classList.remove("quotation-printing"); };
  }, [quotations, onFinish, message]);
  return createPortal(<div className="quotation-print-root" ref={root}>
    {quotations.map(quote => <section className="quotation-print-page" key={quote.id}>
      <QuotationPreview draft={quote} customerName={quote.customerName ?? undefined}/>
    </section>)}
  </div>, document.body);
}
