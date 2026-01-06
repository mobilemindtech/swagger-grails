package swagger.grails.model

import com.fasterxml.jackson.annotation.JsonIgnore
import grails.util.Holders
import groovy.transform.ToString
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.ConstPool
import javassist.bytecode.annotation.Annotation
import javassist.bytecode.annotation.StringMemberValue
import org.grails.core.DefaultGrailsControllerClass
import swagger.grails.SwaggerBuilderHelper
import swagger.grails.SwaggerController
// Importação da nova anotação
import io.swagger.v3.oas.annotations.tags.Tag

@ToString(includes = ['className', 'tag', 'shortName'], includePackage = false)
class SwaggerApi extends SwaggerBuilderHelper {
    String className
    String tag
    String shortName
    List<SwaggerOperation> swaggerOperations = []
    Class clazz

    SwaggerApi(DefaultGrailsControllerClass controllerClass) {
        this.className = controllerClass.clazz.name
        this.clazz = controllerClass.clazz
        this.tag = controllerClass.naturalName
        this.shortName = controllerClass.shortName
        this.swaggerOperations = controllerClass.actions.collect { String actionName ->
            new SwaggerOperation(controllerClass, actionName)
        }.sort {
            it.value
        }
    }

    /**
     * Build the @Tag annotation for OpenAPI 3.
     * Note: In V3, @Tag is the standard for class-level grouping.
     */
    AnnotationsAttribute buildApiAnnotation(ConstPool constPool) {
        AnnotationsAttribute attribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag)

        // Mudança: De Api.class para Tag.class
        Annotation annotation = new Annotation(Tag.class.name, constPool)

        // No OpenAPI 3 (@Tag), usamos "name" em vez de "tags" (que era um array)
        // ou "value". O "name" é o identificador único do grupo.
        annotation.addMemberValue("name", new StringMemberValue(tag.toString(), constPool))

        // Opcional: Adicionar uma descrição padrão
        annotation.addMemberValue("description", new StringMemberValue("Controller para " + shortName, constPool))

        attribute.addAnnotation(annotation)
        attribute
    }

    @JsonIgnore
    static List<SwaggerApi> getApis() {
        Holders.grailsApplication["controllerClasses"].findAll { DefaultGrailsControllerClass dc ->
            dc.clazz.name != SwaggerController.class.name && !dc.clazz.isInterface()
        }.collect { DefaultGrailsControllerClass it ->
            new SwaggerApi(it)
        }.sort {
            it.tag
        }
    }
}