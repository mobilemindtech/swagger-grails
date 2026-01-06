package swagger.grails

import io.swagger.v3.core.util.Json
import swagger.grails.model.SwaggerApi

class SwaggerController {
    SwaggerService swaggerService

    static defaultAction = "index"

    // Novo endpoint para renderizar o HTML do UI
    def index() {
        String jsonUrl = createLink(action: 'api', absolute: true)

        // HTML básico que carrega o Swagger UI dos WebJars
        String html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Swagger UI</title>
            <link rel="stylesheet" type="text/css" href="/swagger/assets/swagger-ui.css" >
            <style>
              html { box-sizing: border-box; overflow-y: scroll; }
              *, *:before, *:after { box-sizing: inherit; }
              body { margin:0; background: #fafafa; }
            </style>
        </head>
        <body>
            <div id="swagger-ui"></div>
            <script src="/swagger/assets/swagger-ui-bundle.js"> </script>
            <script src="/swagger/assets/swagger-ui-standalone-preset.js"> </script>            
            <script>
            window.onload = function() {
              const ui = SwaggerUIBundle({
                url: "${jsonUrl}",
                dom_id: '#swagger-ui',
                deepLinking: true,
                presets: [
                  SwaggerUIBundle.presets.apis,
                  SwaggerUIStandalonePreset
                ],
                plugins: [
                  SwaggerUIBundle.plugins.DownloadUrl
                ],
                layout: "StandaloneLayout"
              })
              window.ui = ui
            }
          </script>
        </body>
        </html>
        """
        render(text: html, contentType: "text/html", encoding: "UTF-8")
    }

    def api() {
        header("Access-Control-Allow-Origin", request.getHeader('Origin'))
        render(status: 200, contentType: "application/json", text: swaggerService.generate())
    }

    def internal() {
        header("Access-Control-Allow-Origin", request.getHeader('Origin'))
        render(status: 200, contentType: "application/json", text: Json.mapper().writeValueAsString(SwaggerApi.apis))
    }

    def assets(){
        String file = request.requestURL.toString().split("/").last()

        if(!file.endsWith('.css') && !file.endsWith('.js')) {
            render status: 404
            return
        }

        def ctype = file.endsWith('.css') ? 'text/css' : 'application/javascript'
        def is = this.class.classLoader.getResourceAsStream("swagger-ui/${file}")

        if (is) {
            render file: is.bytes, contentType: ctype
        } else {
            render status: 404
        }
    }
}
