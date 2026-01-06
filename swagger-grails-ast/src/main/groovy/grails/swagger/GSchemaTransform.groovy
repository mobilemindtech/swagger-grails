package grails.swagger

import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@Slf4j
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class GSchemaTransform implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (!nodes || nodes.length < 2) return

        try{
            if (!(nodes[1] instanceof FieldNode)) return
            FieldNode fieldNode = (FieldNode) nodes[1]
            ClassNode classNode = fieldNode.getOwner()

            // Pega todas as anotações do campo, exceto a própria @GSchema
            List<AnnotationNode> annotations = fieldNode.getAnnotations().findAll {
                it.classNode.name != 'grails.swagger.GSchema'
            }

            if (annotations && classNode) {
                String capitalizeName = fieldNode.name.capitalize()
                MethodNode getter = classNode.getGetterMethod("get" + capitalizeName)
                MethodNode setter = classNode.getSetterMethod("set" + capitalizeName)

                annotations.each { ann ->
                    if (getter) copyAnnotation(ann, getter)
                    if (setter) copyAnnotation(ann, setter)
                }
            }
        }catch (Exception e){
            log.error e.message, e
        }
    }

    private void copyAnnotation(AnnotationNode source, AnnotatedNode target) {
        // Verifica se o alvo já tem essa anotação (pelo nome da classe)
        if (target.getAnnotations(source.getClassNode()).isEmpty()) {
            AnnotationNode copy = new AnnotationNode(source.getClassNode())
            source.getMembers().each { name, expr ->
                copy.addMember(name, expr)
            }
            target.addAnnotation(copy)
        }
    }
}