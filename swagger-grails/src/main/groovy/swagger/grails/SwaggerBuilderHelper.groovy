package swagger.grails

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import grails.util.Holders
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.annotations.tags.Tag
import javassist.ClassPool
import javassist.CtClass
import javassist.LoaderClassPath
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.annotation.Annotation
import org.apache.commons.lang3.RandomStringUtils
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.WebUtils
import org.springframework.context.MessageSource

class SwaggerBuilderHelper {

    /**
     * Returns a list of javassist Annotations that have a base package of io.swagger.annotations
     *
     * @param javaAssistProxy
     * @return List of {@link javassist.bytecode.annotation.Annotation}
     */
    static List<Annotation> fetchExistingSwaggerAnnotations(def javaAssistProxy) {
        javaAssistProxy.getAttributes().findAll {
            it.class == AnnotationsAttribute
        }.collect { AnnotationsAttribute attribute ->
            attribute.getAnnotations()
        }.flatten().findAll { Annotation it ->
            it.typeName.startsWith(Tag.class.package.name)
        }
    }

    /**
     * Returns true if the given javassist proxy contains an annotation matching the given class
     *
     * @param javaAssistProxy Javassist Proxy
     * @param clazz {@link Class}
     * @return boolean
     */
    static boolean hasExistingAnnotation(def javaAssistProxy, Class<?> clazz) {
        javaAssistProxy.getAttributes().findAll {
            it.class == AnnotationsAttribute
        }.collect { AnnotationsAttribute attribute ->
            attribute.getAnnotations()
        }.flatten().find { Annotation it ->
            it.typeName == clazz.name
        }
    }
}
