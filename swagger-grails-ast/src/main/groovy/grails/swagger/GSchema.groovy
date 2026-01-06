package grails.swagger


import org.codehaus.groovy.transform.GroovyASTTransformationClass
import java.lang.annotation.*

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
@GroovyASTTransformationClass("grails.swagger.GSchemaTransform")
public @interface GSchema {} // Sem membros