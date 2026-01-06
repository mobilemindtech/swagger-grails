package swagger.grails

import grails.plugins.Plugin
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.servers.Server

class SwaggerGrailsPlugin extends Plugin {
    def grailsVersion = "5.0.0 > *"
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/views/notFound.gsp",
            "grails-app/conf/logback-test.groovy",
            "**/test/**"
    ]
    def title = "swagger-grails"
    def author = "steamcleaner"
    def authorEmail = ""
    def description = "Grails 5.x.x plugin that will generate and display Swagger documentation."
    def profiles = ['web']
    def documentation = "https://github.com/steamcleaner/swagger-grails"
    def watchedResources = [
            "file:./grails-app/controllers/**/*.groovy"
    ]
    def license = "APACHE"
    def scm = [url: "https://github.com/steamcleaner/swagger-grails"]


    Closure doWithSpring() {
        { ->

            /**
             * swagger:
             *  requireAnnotations: true
             *  info:
             *      title: My api
             *      version: 0.0.1
             *      description: Server api
             *  server:
             *      uri: https://myserver.com/api
             *  security:
             *      apiKey:
             *          enabled: true
             *          name: apiKey
             *          global: false
             *          types:
             *              - header
             *              - query
             *              - cookie
             *      bearer:
             *          enabled: true
             *          name: bearerAuth
             *          global: false
             *      basic:
             *          enabled: true
             *          name: basicAuth
             *          global: false
             */
            def uri = grailsApplication.config.swagger?.server?.uri ?: "/"
            def apiKey = grailsApplication.config.swagger?.security?.apiKey
            def bearer = grailsApplication.config.swagger?.security?.bearer
            def basic = grailsApplication.config.swagger?.security?.basic
            def infoCfg = grailsApplication.config.swagger?.info

            openApi(OpenAPI) { bean ->

                servers = [new Server().url(uri).description(infoCfg?.description ?: "Server API")]
                components = new Components()
                // 4. Aplicando a segurança globalmente
                security = []

                if(apiKey?.enabled) {
                    def apiKeyName = apiKey.name ?: "apiKey"
                    for (def typ in apiKey.types) {

                        def name = apiKey.types.size() > 1 ? "$apiKeyName-$typ" : apiKeyName
                        def type = SecurityScheme.In.values().find{ it.toString() == typ }

                        if(type) {

                            log.debug "Configure swagger security $name, type $typ"

                            SecurityScheme apiKeyScheme = new SecurityScheme()
                                    .type(SecurityScheme.Type.APIKEY)
                                    .name(name)
                                    .in(type)
                                    .description("ApiKey $type authentication")
                            components.addSecuritySchemes(name, apiKeyScheme)
                            if(apiKey.enabled){
                                security.add(new SecurityRequirement().addList(name))
                            }
                        } else {
                            log.warn "Invalid value $type to config swagger.security.apiKey.types "
                        }
                    }
                }

                if(bearer?.enabled){
                    def name = bearer.name ?: "bearerAuth"

                    log.debug "Configure swagger security $name"

                    SecurityScheme apiKeyScheme = new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .name(name)
                            .bearerFormat("JWT")
                            .scheme("bearer")
                            .in(SecurityScheme.In.HEADER)
                            .description("Bearer token authentication")
                    components.addSecuritySchemes("bearer", apiKeyScheme)
                    if(basic.enabled){
                        security.add(new SecurityRequirement().addList(name))
                    }
                }

                if(basic?.enabled){
                    def name = basic.name ?: "basicAuth"

                    log.debug "Configure swagger security $name"

                    SecurityScheme apiKeyScheme = new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .name(name)
                            .scheme("basic")
                            .in(SecurityScheme.In.HEADER)
                            .description("Basic authentication")
                    components.addSecuritySchemes("basic", apiKeyScheme)
                    if(basic.global){
                        security.add(new SecurityRequirement().addList(name))
                    }
                }

                info = new Info().title(infoCfg?.title ?: "API").version(infoCfg?.version ?: "0.1")

                openapi = "3.0.1"
            }

            swaggerCache(SwaggerCache) { bean ->
                openApi = ref('openApi')
            }
        }
    }

    void doWithDynamicMethods() {
    }

    void doWithApplicationContext() {
    }

    void onChange(Map<String, Object> event) {
        if (event.source)
            (event.ctx.getBean("swaggerCache") as SwaggerCache).flush()
    }

    void onConfigChange(Map<String, Object> event) {
    }

    void onShutdown(Map<String, Object> event) {
    }
}
