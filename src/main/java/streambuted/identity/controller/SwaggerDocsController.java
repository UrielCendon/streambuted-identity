package streambuted.identity.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v1/identity")
public class SwaggerDocsController {

    @GetMapping(value = "/docs", produces = MediaType.TEXT_HTML_VALUE)
    public String docs() {
        return """
            <!doctype html>
            <html lang="es">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>StreamButed Identity Service API Docs</title>
                <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css" />
                <style>
                  :root {
                    color-scheme: dark;
                    --sb-bg: #101417;
                    --sb-panel: #171d20;
                    --sb-border: #3a454b;
                    --sb-text: #f3f7f5;
                    --sb-muted: #a8b5b0;
                    --sb-accent: #32d296;
                    --sb-blue: #62a8ff;
                  }
                  body { margin: 0; background: var(--sb-bg); }
                  .swagger-ui { color: var(--sb-text); font-family: Inter, Segoe UI, Arial, sans-serif; }
                  .swagger-ui .topbar { display: none; }
                  .swagger-ui .wrapper, .swagger-ui .information-container, .swagger-ui .scheme-container {
                    background: var(--sb-bg);
                    max-width: none;
                    padding-left: 28px;
                    padding-right: 28px;
                  }
                  .swagger-ui .info { margin: 48px 0 36px; }
                  .swagger-ui .info .title, .swagger-ui .opblock-tag, .swagger-ui h1, .swagger-ui h2,
                  .swagger-ui h3, .swagger-ui h4, .swagger-ui h5, .swagger-ui p, .swagger-ui label,
                  .swagger-ui table thead tr td, .swagger-ui table thead tr th,
                  .swagger-ui .parameter__name, .swagger-ui .parameter__type,
                  .swagger-ui .response-col_status, .swagger-ui .response-col_description,
                  .swagger-ui .tab li, .swagger-ui .model-title, .swagger-ui .model,
                  .swagger-ui .prop-format, .swagger-ui .servers-title {
                    color: var(--sb-text) !important;
                  }
                  .swagger-ui .info .title small, .swagger-ui .info .base-url, .swagger-ui .markdown p,
                  .swagger-ui .opblock-tag small, .swagger-ui .parameter__deprecated,
                  .swagger-ui .prop-type { color: var(--sb-muted) !important; }
                  .swagger-ui .scheme-container {
                    background: var(--sb-panel);
                    border: 1px solid var(--sb-border);
                    box-shadow: none;
                    margin: 0 0 34px;
                    padding-top: 24px;
                    padding-bottom: 24px;
                  }
                  .swagger-ui .opblock,
                  .swagger-ui .opblock .opblock-section-header,
                  .swagger-ui .responses-inner,
                  .swagger-ui .opblock-description-wrapper,
                  .swagger-ui .parameters-container,
                  .swagger-ui .model-box,
                  .swagger-ui section.models {
                    background: var(--sb-panel);
                    border-color: var(--sb-border);
                    box-shadow: none;
                  }
                  .swagger-ui input, .swagger-ui select, .swagger-ui textarea {
                    background: #0f1417 !important;
                    border-color: #c8d2d8 !important;
                    color: var(--sb-text) !important;
                  }
                  .swagger-ui .btn, .swagger-ui .auth-wrapper .authorize {
                    background: transparent;
                    border-color: var(--sb-accent);
                    color: var(--sb-accent);
                  }
                  .swagger-ui a, .swagger-ui .info a { color: var(--sb-blue) !important; }
                  .swagger-ui .filter .operation-filter-input {
                    background: #11171a !important;
                    border: 2px solid #c8d2d8 !important;
                    color: var(--sb-text) !important;
                  }
                  .swagger-ui .dialog-ux .modal-ux,
                  .swagger-ui .dialog-ux .modal-ux-header,
                  .swagger-ui .dialog-ux .modal-ux-content {
                    background: var(--sb-panel);
                    border-color: var(--sb-border);
                    color: var(--sb-text);
                  }
                </style>
              </head>
              <body>
                <div id="swagger-ui"></div>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-standalone-preset.js"></script>
                <script src="/api/v1/identity/docs/swagger-ui.js"></script>
              </body>
            </html>
            """;
    }

    @GetMapping(value = "/docs/swagger-ui.js", produces = "application/javascript")
    public String swaggerUiInitializer() {
        return """
            window.addEventListener("load", () => {
              window.ui = SwaggerUIBundle({
                url: "/api/v1/identity/openapi.json",
                dom_id: "#swagger-ui",
                deepLinking: true,
                displayRequestDuration: true,
                filter: true,
                persistAuthorization: true,
                presets: [
                  SwaggerUIBundle.presets.apis,
                  SwaggerUIStandalonePreset
                ],
                layout: "StandaloneLayout"
              });
            });
            """;
    }
}
