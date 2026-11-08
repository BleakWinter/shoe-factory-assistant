import { App } from "antd";
import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import type { Quotation } from "../types/quotation";
import QuotationPreview from "./QuotationPreview";

export type QuotationBatchPrintJob = { quotations: Quotation[]; mode: "quotation" | "signed" };

export default function QuotationBatchPrint({ job, onFinish }: { job: QuotationBatchPrintJob; onFinish: () => void }) {
  const { quotations, mode } = job;
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
        await Promise.all(images.map((image, index) => image.decode().catch(() => {
          if (mode === "signed") {
            const quote = quotations[index];
            throw new Error(`${quote.developmentNo || "未填款号"}（V${quote.versionNo}）的签字照片加载失败，请重试`);
          }
        })));
        await document.fonts.ready;
        await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
        if (active) window.print();
      } catch (error) {
        if (active) { message.error(error instanceof Error ? error.message : "批量打印失败"); finish(); }
      }
    }
    void print();
    return () => { active = false; window.removeEventListener("afterprint", finish); document.body.classList.remove("quotation-printing"); };
  }, [quotations, mode, onFinish, message]);
  return createPortal(<div className="quotation-print-root" ref={root}>
    {quotations.map(quote => <section className="quotation-print-page" key={quote.id}>
      {mode === "signed"
        ? <img className="quotation-signed-print-image" src={quote.signedImageUrl ?? undefined} alt="客户签字报价单"/>
        : <QuotationPreview draft={quote} customerName={quote.customerName ?? undefined}/>}
    </section>)}
  </div>, document.body);
}
