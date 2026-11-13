package com.shoefactory.assistant.service.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
class DeliveryReportRenderer {
    private static final MediaType PNG = MediaType.valueOf("image/png");
    private static final byte[] PNG_SIGNATURE = HexFormat.of().parseHex("89504e470d0a1a0a");

    private final RestClient client;
    private final URI url;

    DeliveryReportRenderer(RestClient.Builder builder, @Value("${app.delivery-report.render-url}") URI url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.client = builder.requestFactory(factory).build();
        this.url = url;
    }

    List<byte[]> render(DeliveryReport report) {
        List<byte[]> pages = new ArrayList<>();
        render(report, pages);
        return pages;
    }

    private void render(DeliveryReport report, List<byte[]> pages) {
        try {
            var result = client.post().uri(url).contentType(MediaType.APPLICATION_JSON).accept(PNG)
                    .body(report).retrieve().toEntity(byte[].class);
            byte[] image = result.getBody();
            MediaType contentType = result.getHeaders().getContentType();
            if (contentType == null || !PNG.equalsTypeAndSubtype(contentType) || image == null
                    || image.length <= PNG_SIGNATURE.length || !Arrays.equals(image, 0, PNG_SIGNATURE.length,
                    PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
                throw new IllegalStateException("绘图服务返回的内容不是 PNG");
            }
            pages.add(image);
        } catch (RestClientResponseException ex) {
            List<List<DeliveryReport.OrderGroup>> parts = ex.getStatusCode().value() == 422
                    && ex.getResponseBodyAsString().contains("日报超出单张图片高度") ? split(report.orderGroups()) : List.of();
            if (parts.isEmpty()) {
                throw new IllegalStateException("绘图服务返回 HTTP " + ex.getStatusCode().value());
            }
            for (var part : parts) {
                render(new DeliveryReport(report.reportDate(), part), pages);
            }
        } catch (RestClientException ex) {
            throw new IllegalStateException("无法连接货期日报绘图服务", ex);
        }
    }

    private List<List<DeliveryReport.OrderGroup>> split(List<DeliveryReport.OrderGroup> groups) {
        if (groups.size() > 1) {
            int middle = groups.size() / 2;
            return List.of(groups.subList(0, middle), groups.subList(middle, groups.size()));
        }
        if (groups.isEmpty() || groups.get(0).styles().size() < 2) {
            return List.of();
        }
        var group = groups.get(0);
        int middle = group.styles().size() / 2;
        return List.of(
                List.of(new DeliveryReport.OrderGroup(group.orderNo(), group.deliveryDate(), group.overdueDays(),
                        group.styles().subList(0, middle))),
                List.of(new DeliveryReport.OrderGroup(group.orderNo(), group.deliveryDate(), group.overdueDays(),
                        group.styles().subList(middle, group.styles().size()))));
    }
}
