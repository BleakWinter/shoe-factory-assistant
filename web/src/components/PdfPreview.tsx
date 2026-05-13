import { Empty, Spin } from "antd";
import { useEffect, useState } from "react";

interface PdfPreviewProps {
  url?: string;
}

export default function PdfPreview({ url }: PdfPreviewProps) {
  // url 变化时重新显示 loading，iframe load 后隐藏。
  const [loading, setLoading] = useState(Boolean(url));

  useEffect(() => {
    setLoading(Boolean(url));
  }, [url]);

  if (!url) {
    // 上传后还没点击“订单/装箱单”时会走这里。
    return (
      <div className="pdf-empty">
        <Empty description="暂无 PDF 预览" />
      </div>
    );
  }

  return (
    <div className="pdf-preview">
      {loading && (
        <div className="pdf-loading">
          <Spin tip="正在打开预览" />
        </div>
      )}
      <iframe
        title="PDF 预览"
        src={url}
        onLoad={() => setLoading(false)}
        className="pdf-frame"
      />
    </div>
  );
}
