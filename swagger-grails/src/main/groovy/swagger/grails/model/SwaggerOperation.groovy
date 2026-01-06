package swagger.grails.model

import grails.web.mapping.UrlMapping
import groovy.transform.ToString
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import javassist.CtMethod
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ConstPool
import javassist.bytecode.annotation.*
import org.grails.core.DefaultGrailsControllerClass
import swagger.grails.SwaggerBuilderHelper
import swagger.grails.SwaggerMapping
import java.util.regex.Pattern

@ToString(includes = ['value', 'nickname', 'httpMethod', 'actionName'], includePackage = false)
class SwaggerOperation extends SwaggerBuilderHelper implements SwaggerMapping {
    private DefaultGrailsControllerClass controllerClass
    private UrlMapping urlMapping

    /**
     * Grails action, eg.: index
     */
    String value
    /**
     * Grails router, eg.: /edit/{id}
     */
    String nickname
    /**
     * http method, eg.: GET
     */
    String httpMethod
    List<SwaggerParameter> swaggerParameters = []
    List<String> commandObjects = []

    SwaggerOperation(DefaultGrailsControllerClass controllerClass, String actionName) {
        this.controllerClass = controllerClass
        this.urlMapping = mappingForControllerAndAction(controllerClass.logicalPropertyName, actionName)
        this.value = actionName
        this.nickname = buildNickname()
        this.httpMethod = urlMapping?.httpMethod ?: genericHttpMethod
        this.swaggerParameters = SwaggerParameterBuilder.buildSwaggerParameters(nickname, controllerClass, actionName, this.urlMapping)
    }

    String getActionName() { value }

    private String buildNickname() {
        if (!urlMapping) return genericNickname
        String nickname = (urlMapping.urlData.urlPattern.split("/").findAll { it }.inject([0, []]) { List _list, piece ->
            if (piece ==~ /\(\*\)/ || piece ==~ Pattern.quote("(*)(.(*))?"))
                [_list[0] + 1, _list[1] << "{${urlMapping.constraints[_list[0]].propertyName}}"]
            else
                [_list[0], _list[1] << piece.replace("(.(*))?", "")]
        })[1].join("/")
        nickname.startsWith("/") ? nickname : "/$nickname"
    }

    private String getGenericNickname() {
        switch (actionName) {
            case "index": case "save": return "/${controllerClass.logicalPropertyName}"
            case "delete": case "show": case "update": case "patch": return "/${controllerClass.logicalPropertyName}/{id}"
            default: return "/${controllerClass.logicalPropertyName}/$actionName"
        }
    }

    private String getGenericHttpMethod() {
        switch (value) {
            case "index": return "GET"
            case "save": return "POST"
            case "show": return "GET"
            case "delete": return "DELETE"
            case "update": return "PUT"
            case "patch": return "PATCH"
            default: return "GET"
        }
    }

    /**
     * Build @Operation and handle Parameters/RequestBody for V3
     */
    AnnotationsAttribute buildOperationAnnotation(CtMethod method, ConstPool constPool) {
        AnnotationsAttribute attribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag)

        // 1. Gerar @Operation (antigo @ApiOperation)
        if (!hasExistingAnnotation(method.methodInfo, Operation.class)) {
            Annotation opAnn = new Annotation(Operation.class.name, constPool)
            opAnn.addMemberValue("summary", new StringMemberValue(value, constPool))
            opAnn.addMemberValue("operationId", new StringMemberValue(nickname, constPool))
            // No V3 o método HTTP não fica na anotação @Operation, mas o Reader do JAX-RS/Spring o identificará.
            attribute.addAnnotation(opAnn)
        }

        // 2. Separar Parâmetros de Body
        def bodyParams = swaggerParameters.findAll { it.paramType == "body" }
        def queryPathParams = swaggerParameters.findAll { it.paramType != "body" }

        // 3. Gerar @Parameters (antigo @ApiImplicitParams)
        if (queryPathParams) {
            buildParametersArrayAnnotation(queryPathParams, constPool).annotations.each {
                attribute.addAnnotation(it)
            }
        }

        // 4. Gerar @RequestBody (Novo no V3)
        if (bodyParams) {
            bodyParams.each { bp ->
                commandObjects << bp.dataType
                attribute.addAnnotation(buildRequestBodyAnnotation(bp, constPool))
            }
        }

        fetchExistingSwaggerAnnotations(method.methodInfo).each { attribute.addAnnotation(it) }

        attribute
    }

    /**
     * Build @Parameters annotation containing a list of @Parameter
     */
    private AnnotationsAttribute buildParametersArrayAnnotation(List<SwaggerParameter> params, ConstPool pool) {
        AnnotationsAttribute attr = new AnnotationsAttribute(pool, AnnotationsAttribute.visibleTag)
        Annotation parametersAnn = new Annotation(Parameters.class.name, pool)

        ArrayMemberValue arrayVal = new ArrayMemberValue(pool)
        AnnotationMemberValue[] annMembers = params.collect { sp ->
            Annotation pAnn = new Annotation(Parameter.class.name, pool)
            pAnn.addMemberValue("name", new StringMemberValue(sp.name, pool))
            pAnn.addMemberValue("description", new StringMemberValue(sp.name, pool))
            pAnn.addMemberValue("required", new BooleanMemberValue(sp.required, pool))

            // Mapeia o "in" (QUERY, PATH, HEADER, COOKIE)
            String inEnum = sp.paramType.toUpperCase()
            EnumMemberValue ev = new EnumMemberValue(pool)
            ev.setType(ParameterIn.class.name)
            ev.setValue(inEnum)
            pAnn.addMemberValue("in", ev)

            // No V3, o tipo vai dentro de @Schema
            Annotation schemaAnn = new Annotation(Schema.class.name, pool)
            schemaAnn.addMemberValue("type", new StringMemberValue(mapToV3Type(sp.dataType), pool))
            pAnn.addMemberValue("schema", new AnnotationMemberValue(schemaAnn, pool))

            new AnnotationMemberValue(pAnn, pool)
        } as AnnotationMemberValue[]

        parametersAnn.addMemberValue("value", arrayVal.with { it.setValue(annMembers); it })
        attr.addAnnotation(parametersAnn)
        attr
    }

    /**
     * Build @RequestBody annotation for V3
     */
    private Annotation buildRequestBodyAnnotation(SwaggerParameter sp, ConstPool pool) {
        Annotation rbAnn = new Annotation(RequestBody.class.name, pool)
        rbAnn.addMemberValue("description", new StringMemberValue("Corpo da requisição", pool))
        rbAnn.addMemberValue("required", new BooleanMemberValue(true, pool))

        Annotation contentAnn = new Annotation(Content.class.name, pool)
        Annotation schemaAnn = new Annotation(Schema.class.name, pool)

        // Para objetos complexos, usamos implementation (referência de classe)
        ClassMemberValue cmv = new ClassMemberValue(sp.dataType, pool)
        schemaAnn.addMemberValue("implementation", cmv)

        contentAnn.addMemberValue("schema", new AnnotationMemberValue(schemaAnn, pool))

        ArrayMemberValue contentArray = new ArrayMemberValue(pool)
        contentArray.setValue([new AnnotationMemberValue(contentAnn, pool)] as AnnotationMemberValue[])
        rbAnn.addMemberValue("content", contentArray)

        rbAnn
    }

    private String mapToV3Type(String oldType) {
        // Simples mapeador de tipos Swagger 2 para tipos primitivos OpenAPI 3
        switch(oldType?.toLowerCase()) {
            case "int": case "integer": return "integer"
            case "long": return "integer"
            case "float": case "double": return "number"
            case "boolean": return "boolean"
            default: return "string"
        }
    }
}