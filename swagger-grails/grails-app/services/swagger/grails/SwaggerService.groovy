package swagger.grails

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import grails.core.GrailsApplication
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.links.Link
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import swagger.grails.model.SwaggerApi
import swagger.grails.model.SwaggerOperation
import swagger.grails.model.SwaggerParameter

import java.lang.reflect.Field

class SwaggerService {
    SwaggerCache swaggerCache
    GrailsApplication grailsApplication


    static {
        ModelConverters.getInstance().addConverter(new OnlySchemaAnnotationFilter())
    }
    /**
     * Generates a String representation of the swagger annotated controllers and actions.
     * <br><br>
     * As a way to limit the load on the implementing application
     * the swagger spec is cached as long as no changes are detected
     * in the url mappings or any controller.
     *
     * @return Returns the built swagger spec as a String
     */
    String generate() {

        def requireAnnotations = grailsApplication.config.getProperty('swagger.requireAnnotations', Boolean)

        if(requireAnnotations)
            log.info "Swagger only see only annotated controller classes"
        else
            log.info "Swagger only see all controller classes"

        Json.mapper().writeValueAsString(
                swaggerCache.getOrElse { OpenAPI openApi ->
                    if (openApi.paths == null) openApi.paths = new Paths()
                    if (openApi.components == null) openApi.components = new Components()
                    
                    List<SwaggerApi> apis = SwaggerApi.getApis()
                            .findAll { api ->
                                !requireAnnotations || api.clazz.isAnnotationPresent(Tag.class)
                            }

                    apis.each { SwaggerApi api ->
                        api.swaggerOperations.each { SwaggerOperation op ->

                            String pathKey = op.nickname ?: "/${api.shortName}/${op.actionName}"

                            def method = api.clazz.methods.find { it.name == op.actionName }
                            def operationAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.Operation)
                            def paramsAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.Parameters)
                            def paramAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.Parameter)
                            def reqBodyAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.parameters.RequestBody)
                            def responsesAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses)
                            def responseAnnot = method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse)

                            def secRequitiments = method.getAnnotation(io.swagger.v3.oas.annotations.security.SecurityRequirements)
                            def secRequitiment = method.getAnnotation(io.swagger.v3.oas.annotations.security.SecurityRequirement)

                            if(!requireAnnotations || operationAnnot) {

                                Operation operation = new Operation()
                                        .summary(op.value ?: op.actionName)
                                        .addTagsItem(api.tag)

                                operationAnnotToModel(openApi, operationAnnot, operation)

                                if (paramsAnnot) {
                                    operation.setParameters(paramsAnnot.value().collect { convertParameter(openApi, it) })
                                } else if (paramAnnot) {
                                    operation.setParameters([convertParameter(openApi, paramAnnot)])
                                }

                                if (reqBodyAnnot) {
                                    operation.requestBody(convertRequestBody(openApi, reqBodyAnnot))
                                }

                                if (secRequitiments) {
                                    secRequitiments.value().each {
                                        def item = convertSecurity(it)
                                        operation.addSecurityItem(item)
                                    }
                                } else if (secRequitiment) {
                                    operation.addSecurityItem(convertSecurity(secRequitiment))
                                }

                                op.swaggerParameters.each { SwaggerParameter p ->
                                    if (p.paramType == "body" && !reqBodyAnnot) {
                                        // MAPEAMENTO DE TIPO COMPLEXO (Request Body)
                                        operation.requestBody(createRequestBody(openApi, p.dataType))
                                    } else if (!paramsAnnot && !paramAnnot) {
                                        operation.addParametersItem(new Parameter()
                                                .name(p.name)
                                                .in(p.paramType)
                                                .required(p.required)
                                                .schema(new Schema().type(mapDataType(p.dataType))))
                                    }
                                }

                                if (responsesAnnot) {
                                    ApiResponses responses = new ApiResponses()
                                    responsesAnnot.value().each {
                                        def resp = convertResponse(openApi, it)
                                        responses.addApiResponse(it.responseCode(), resp)
                                    }
                                    operation.responses(responses)
                                } else if (responseAnnot) {
                                    operation.responses(
                                            new ApiResponses().addApiResponse(responseAnnot.responseCode(),
                                                    convertResponse(openApi, responseAnnot)))

                                } else {
                                    operation.responses(new ApiResponses().addApiResponse("200",
                                            new ApiResponse().description("OK")))
                                }

                                PathItem pathItem = openApi.paths.get(pathKey) ?: new PathItem()
                                assignMethod(pathItem, op.httpMethod, operation)
                                openApi.paths.addPathItem(pathKey, pathItem)
                            }
                        }
                    }
                    return openApi
                }
        )
    }

    /**
     * Extrai a estrutura da classe e adiciona aos componentes globais
     */
    private RequestBody createRequestBody(OpenAPI openApi, String className) {
        try {

            Class clazz = Class.forName(className, true, Thread.currentThread().contextClassLoader)
            /*
            def schema = classToScheme(className)
            openApi.components.addSchemas(clazz.simpleName schema)
            return new RequestBody()
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/${clazz.simpleName}"))))
            */

            registerSchemas(openApi, className)

            return new RequestBody()
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new ObjectSchema().$ref("#/components/schemas/${clazz.simpleName}"))))

        } catch (Exception e) {
            // Fallback caso a classe não seja encontrada
            return new RequestBody()
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema().type("object").description("Erro ao carregar: ${className}"))))
        }
    }

    private Schema classToScheme(String className){
        Class clazz = Class.forName(className, true, Thread.currentThread().contextClassLoader)

        if (clazz.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema)) {
            def classAnnot = clazz.getAnnotation(io.swagger.v3.oas.annotations.media.Schema)

            // 1. Criar o Schema do Objeto
            Schema objectSchema = new Schema()
            objectSchema.setType("object")
            objectSchema.setDescription(classAnnot.description() ?: null)
            objectSchema.setName(clazz.simpleName)

            // 2. Mapear Propriedades
            clazz.declaredFields.each { Field field ->
                if (field.isAnnotationPresent(io.swagger.v3.oas.annotations.media.Schema)) {
                    def fieldAnnot = field.getAnnotation(io.swagger.v3.oas.annotations.media.Schema)

                    // Criar Schema para o campo
                    Schema propertySchema = new Schema()

                    // Definir tipo baseado no Java
                    String javaType = field.type.simpleName.toLowerCase()
                    fillOpenApiTypes(propertySchema, javaType)

                    // Sobrepor com dados da anotação se existirem
                    if (fieldAnnot.description()) propertySchema.setDescription(fieldAnnot.description())
                    if (fieldAnnot.example()) propertySchema.setExample(fieldAnnot.example())

                    // Adicionar ao objeto pai
                    objectSchema.addProperty(field.name, propertySchema)
                }
            }

            return objectSchema
        }
    }

    // Helper para converter tipos
    private void fillOpenApiTypes(Schema schema, String type) {
        switch (type) {
            case "string":  schema.setType("string"); break
            case "long":    schema.setType("integer"); schema.setFormat("int64"); break
            case "int":
            case "short":
            case "integer": schema.setType("integer"); schema.setFormat("int32"); break
            case "boolean": schema.setType("boolean"); break
            case "float":
            case "double":  schema.setType("number"); schema.setFormat("double"); break
            case "list":
            case "set":     schema.setType("array"); break
            default:        schema.setType("object"); break
        }
    }

    private String mapDataType(String type) {
        switch(type?.toLowerCase()) {
            case "int": case "integer": case "long": return "integer"
            case "boolean": return "boolean"
            case "double": case "float": return "number"
            default: return "string"
        }
    }

    private void assignMethod(PathItem item, String method, Operation op) {
        String m = method?.toUpperCase() ?: "GET"
        switch(m) {
            case "POST": item.setPost(op); break
            case "PUT": item.setPut(op); break
            case "DELETE": item.setDelete(op); break
            default: item.setGet(op); break
        }
    }

    private void operationAnnotToModel(OpenAPI openApi, io.swagger.v3.oas.annotations.Operation annot,  Operation model) {

        if(!annot)
            return

        model.setSummary(annot.summary())
        model.setDescription(annot.description())
        model.setOperationId(annot.operationId())
        model.setDeprecated(annot.deprecated())

        if (annot.tags()) {
            model.setTags(Arrays.asList(annot.tags()))
        }

        annot.requestBody().each {
            model.setRequestBody(convertRequestBody(openApi, it))
        }

        model.setParameters(annot.parameters().collect{ convertParameter(openApi, it) })

        if(annot.responses()) {
            def responses = new ApiResponses()
            annot.responses().each {
                def resp = convertResponse(openApi, it)
                responses.addApiResponse(it.responseCode(), resp)
            }
            model.setResponses(responses)
        }

        model.setSecurity(annot.security().collect { convertSecurity(it) })
    }


    // --- RESPONSE --
    private ApiResponse convertResponse(OpenAPI openApi, io.swagger.v3.oas.annotations.responses.ApiResponse annot) {
        ApiResponse model = new ApiResponse()
        model.setDescription(annot.description())
        model.set$ref(annot.ref())
        model.setContent(convertContent(openApi, annot.content()))
        model.setHeaders(convertHeaders(openApi, annot.headers()))
        model.setLinks(convertLinks(annot.links()))
        return model
    }


    // --- REQUEST BODY ---
    private RequestBody convertRequestBody(OpenAPI openApi, io.swagger.v3.oas.annotations.parameters.RequestBody annot) {
        var model = new RequestBody()
        model.setDescription(annot.description())
        model.setRequired(annot.required())
        if (annot.content()) {
            model.setContent(convertContent(openApi, annot.content()))
        }
        return model
    }

    // --- CONTENT (Mapeia o array de @Content para o mapa do Modelo) ---
    private Content convertContent(OpenAPI openApi, io.swagger.v3.oas.annotations.media.Content[] annots) {
        var content = new Content()
        annots.each { c ->
            var mediaType = new MediaType()
            mediaType.setSchema(convertSchema(openApi, c.schema()))
            content.addMediaType(c.mediaType() ?: "*/*", mediaType)
        }
        return content
    }

    // --- SCHEMA ---
    private Schema convertSchema(OpenAPI openApi, io.swagger.v3.oas.annotations.media.Schema annot) {
        var schema = new Schema()
        if (annot.implementation() != Void.class) {
            // No Java 24, referenciamos pelo nome simples para Components/Schemas
            schema.set$ref("#/components/schemas/" + annot.implementation().simpleName)
            registerSchemas(openApi, annot.implementation().name)
        } else {
            if (annot.type()) schema.setType(annot.type())
            if (annot.format()) schema.setFormat(annot.format())
        }
        return schema
    }

    private void registerSchemas(OpenAPI openApi, String className) {
        try {
            Class clazz = Class.forName(className, true, Thread.currentThread().contextClassLoader)

            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(clazz)

            schemas.each { name, schema ->
                // Adiciona ao mapa global de componentes
                openApi.components = openApi.components ?: new Components()
                openApi.components.addSchemas(name, schema)

            }
        } catch (Exception e) {
            log.error e.message, e
        }
    }

    // --- PARAMETER ---
    private Parameter convertParameter(OpenAPI openApi, io.swagger.v3.oas.annotations.Parameter annot) {
        var model = new Parameter()
        model.setName(annot.name())
        model.setIn(annot.in().toString())
        model.setRequired(annot.required())
        model.setDescription(annot.description())
        model.setSchema(convertSchema(openApi, annot.schema()))
        return model
    }

    // --- SECURITY ---
    private SecurityRequirement convertSecurity(io.swagger.v3.oas.annotations.security.SecurityRequirement annot) {
        var req = new SecurityRequirement()
        req.addList(annot.name(), Arrays.asList(annot.scopes()))
        return req
    }

    // --- HEADERS ---
    private Map<String, Header> convertHeaders(OpenAPI openApi, io.swagger.v3.oas.annotations.headers.Header[] annots) {
        if (!annots) return null
        Map<String, Header> headers = [:]
        annots.each { h ->
            Header model = new Header()
            model.setDescription(h.description())
            model.setDeprecated(h.deprecated())
            model.setRequired(h.required())
            if (h.ref()) model.set$ref(h.ref())

            // No OpenAPI 3, um Header pode ter um Schema ou um Content
            model.setSchema(convertSchema(openApi, h.schema()))

            headers.put(h.name(), model)
        }
        return headers
    }

    // --- LINKS ---
    private Map<String, Link> convertLinks(io.swagger.v3.oas.annotations.links.Link[] annots) {
        if (!annots) return null
        Map<String, Link> links = [:]
        annots.each { l ->
            Link model = new Link()
            model.setOperationId(l.operationId())
            model.setOperationRef(l.operationRef())
            model.setDescription(l.description())
            if (l.ref()) model.set$ref(l.ref())

            // Parâmetros do Link (LinkParameters)
            if (l.parameters()) {
                l.parameters().each { p ->
                    model.addParameter(p.name(), p.expression())
                }
            }

            links.put(l.name(), model)
        }
        return links
    }

}
