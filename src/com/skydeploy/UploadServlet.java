package com.skydeploy;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 50 * 1024 * 1024,
    maxRequestSize = 200 * 1024 * 1024
)
public class UploadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect(request.getContextPath() + "/index.html");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html; charset=UTF-8");

        String projectId = "project_" + UUID.randomUUID();
        String uploadPath = getServletContext().getRealPath("/") + File.separator + projectId;
        Path projectPath = Paths.get(uploadPath);
        List<String> uploadedFiles = new ArrayList<String>();

        try (PrintWriter out = response.getWriter()) {
            Files.createDirectories(projectPath);

            for (Part part : request.getParts()) {
                String fileName = getSubmittedFileName(part);
                if (fileName == null || fileName.trim().isEmpty() || part.getSize() == 0) {
                    continue;
                }

                String safeFileName = sanitizeFileName(fileName);
                if (safeFileName.isEmpty()) {
                    continue;
                }

                Path targetFile = projectPath.resolve(safeFileName).normalize();
                if (!targetFile.startsWith(projectPath)) {
                    throw new ServletException("Invalid file path detected: " + fileName);
                }

                part.write(targetFile.toString());
                uploadedFiles.add(safeFileName);
            }

            if (uploadedFiles.isEmpty()) {
                Files.deleteIfExists(projectPath);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writePage(
                    out,
                    "Upload Failed",
                    "<p>No files were selected for upload.</p>"
                        + "<p><a href=\""
                        + request.getContextPath()
                        + "/index.html\">Go back</a></p>"
                );
                return;
            }

            String projectURL = request.getScheme() + "://"
                + request.getServerName() + ":"
                + request.getServerPort()
                + request.getContextPath() + "/" + projectId;

            StringBuilder body = new StringBuilder();
            body.append("<p>Your files have been uploaded successfully.</p>");
            body.append("<p><strong>Project ID:</strong> ").append(escapeHtml(projectId)).append("</p>");
            body.append("<h3>Live URL:</h3>");
            body.append("<p><a href=\"")
                .append(escapeHtml(projectURL))
                .append("\" target=\"_blank\">")
                .append(escapeHtml(projectURL))
                .append("</a></p>");
            body.append("<p><strong>Saved To:</strong> ")
                .append(escapeHtml(projectPath.toString()))
                .append("</p>");
            body.append("<p><strong>Files:</strong></p><ul>");
            for (String uploadedFile : uploadedFiles) {
                body.append("<li>").append(escapeHtml(uploadedFile)).append("</li>");
            }
            body.append("</ul>");
            body.append("<p><a href=\"")
                .append(request.getContextPath())
                .append("/index.html\">Upload more files</a></p>");

            writePage(out, "Upload Successful", body.toString());
        } catch (Exception ex) {
            log("Upload failed", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            try (PrintWriter out = response.getWriter()) {
                writePage(
                    out,
                    "Upload Failed",
                    "<p>The upload could not be completed.</p><pre>"
                        + escapeHtml(ex.getMessage() == null ? "Unknown error" : ex.getMessage())
                        + "</pre><p><a href=\""
                        + request.getContextPath()
                        + "/index.html\">Try again</a></p>"
                );
            }
        }
    }

    private String getSubmittedFileName(Part part) {
        String submitted = part.getSubmittedFileName();
        if (submitted == null) {
            return null;
        }

        return Paths.get(submitted).getFileName().toString();
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replace("\\", "_").replace("/", "_").replace("..", "_").trim();
    }

    private void writePage(PrintWriter out, String title, String bodyHtml) {
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>" + escapeHtml(title) + "</title></head><body style=\"font-family:Arial,sans-serif;max-width:720px;margin:40px auto;line-height:1.5;\">");
        out.println("<h2>" + escapeHtml(title) + "</h2>");
        out.println(bodyHtml);
        out.println("</body></html>");
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(ch);
                    break;
            }
        }
        return escaped.toString();
    }
}
