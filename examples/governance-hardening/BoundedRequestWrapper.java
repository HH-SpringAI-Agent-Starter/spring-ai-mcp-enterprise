package com.example.mcp.hardening;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 请求体二次校验：即使 Content-Length 被伪造或分块传输，也按实际读取字节数封顶。
 *
 * 上限内把 body 预读进内存，后续 getInputStream() / getReader() 可重复读，
 * 便于「工具入参出参全留痕」的审计要求（Spring AI 2.0 生产约束清单）。
 *
 * ⚠️ 参考实现，位于 examples/ 下，不进 Maven reactor、不参与 CI 构建。
 */
public class BoundedRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;
    private final String sessionKey;

    public BoundedRequestWrapper(HttpServletRequest request, long maxBytes, String sessionKey)
            throws IOException {
        super(request);
        this.sessionKey = sessionKey;
        this.body = readBounded(super.getInputStream(), maxBytes);
    }

    private static byte[] readBounded(ServletInputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("MCP request body exceeds " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bin = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return bin.read(); }
            @Override public boolean isFinished() { return bin.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("synchronous read only");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset cs = StandardCharsets.UTF_8;
        String enc = getCharacterEncoding();
        if (enc != null) {
            try {
                cs = Charset.forName(enc);
            } catch (Exception ignored) {
                // 非法编码名回退 UTF-8
            }
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), cs));
    }

    @Override
    public long getContentLengthLong() { return body.length; }

    @Override
    public int getContentLength() { return body.length; }

    /** 暴露按用户主体绑定的会话指纹，供下游 mcp-tenant / 审计使用。 */
    @Override
    public String getHeader(String name) {
        if ("X-MCP-Session-Key".equalsIgnoreCase(name)) {
            return sessionKey;
        }
        return super.getHeader(name);
    }
}
