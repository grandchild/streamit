/**
 * 
 */
package at.dms.kjc.sir.lowering;

import at.dms.kjc.sir.*;
import at.dms.kjc.*;
import at.dms.util.GetSteadyMethods;
import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFilterIter;

import java.util.*;

/**
 * Determine if code is naively vectorizable by interleaving executions of different steady states.
 * <br/> $Id$
 * @author Allyn Dimock
 *
 */
public class Vectorizable {
    /**
     * Return set of naively vectorizable filters in stream.
     * See {@link #vectorizable(SIRFilter) vectorizable} for what makes a filter naively vectorizable.
     * @param str  Stream to check
     * @return  Set of filters passing {@link #vectorizable(SIRFilter) vectorizable} test
     */
    public static Set<SIRFilter> vectorizableStr(SIRStream str) {
        final Set<SIRFilter> vectorizableFilters = new HashSet<SIRFilter>();
        IterFactory.createFactory().createIter(str).accept(
            new EmptyStreamVisitor() {
                public void visitFilter(SIRFilter self, SIRFilterIter iter) {
                    if (at.dms.kjc.sir.lowering.Vectorizable.vectorizable(self)) {
                        vectorizableFilters.add(self);
                    }
                }
            });
    return vectorizableFilters;
    }
    
    /**
     * Check whether a filter could be naively vectorized by interleaving executions of different steady states.
     * (Does not answer question as to whether it is profitable to do so.)
     * <br/>
     * A filter is naively vectorizable if:
     * <ul><li> Its input type or its output type is a 32-bit int or float.
     * </li><li> It has no loop-carried dependencies between steady states.
     * </li><li> It has no visible side effects.
     * </li><li> It has no data-dependent branches. (Should preclude dynamic-rate filters.)
     * </li></ul>
     * 
     * @param f : a filter to check.
     * @return true if there are no local conditions precluding vectorizing the filter.
     */
    public static boolean vectorizable (SIRFilter f) {
        // only vectorizing if have a 32-bit type to process and creating a 32
        // bit type if any.  (Actually more: should not cast to non-32-bit 
        // simple type internally, but can ignore since StreamIt simple types
        // are all 32 bit currently -- and special cases for casting to math functions.
        CType inputType = f.getInputType();
        CType outputType = f.getOutputType();
        if (!(inputType instanceof CIntType || inputType instanceof CFloatType
        ||  outputType  instanceof CIntType || outputType instanceof CFloatType)) {
//          // debugging:
          System.err.println("Vectorizable.vectorizable found " + f.getName() + " has wrong type.");
            return false;
        }
        // must have static rates
        if (f.getPeek().isDynamic() ||
            f.getPop().isDynamic() ||
            f.getPush().isDynamic()) {
            return false;
        }

        // only vectorizing if no loop-carried dependencies (no stores to fields).
        if (at.dms.kjc.sir.lowering.fission.StatelessDuplicate.hasMutableState(f)) {
//            // debugging:
            System.err.println("Vectorizable.vectorizable found " + f.getName() + " has mutable state.");
            return false; // If possible loop-carried dependence through fields: don't vectorize. 
        }
        // only vectorizing if no side effects (prints, file reads, file writes).
        if (hasSideEffects(f)) {
//            // debugging:
            System.err.println("Vectorizable.vectorizable found " + f.getName() + " has side effects.");
            return false; // If filter has side effects, don't vectorize.
        }
        // only vectorizing if branches, peek and array offsets are not data dependent.
        if (isDataDependent(f)) {
            // debugging:
            System.err.println("Vectorizable.vectorizable found " + f.getName() + " has control or offset dependence.");
            return false; // Filter has branches that are data dependent, don't vectorize.
        }
        //      debugging:
        System.err.println("Vectorizable.vectorizable found " + f.getName() + " is vectorizable!.");
        return true;
    }
    
    /**
     * Check whether a filter has side effects (I/O).
     * This includes message send and receive.
     * @param f : a filter to check
     * @return  true if no side effects, and no messages.
     */
    public static boolean hasSideEffects (SIRFilter f) {
        if (f instanceof SIRFileReader || f instanceof SIRFileWriter) {
            return true;
        }
        final boolean[] tf = {false};
        List<JMethodDeclaration> methods = GetSteadyMethods.getSteadyMethods(f);
        for (JMethodDeclaration method : methods)
            method.accept(new SLIREmptyVisitor() {
                public void visitPrintStatement(SIRPrintStatement self,
                        JExpression exp) { 
                    tf[0] = true;
                }
                public void visitMessageStatement(SIRMessageStatement self,
                        JExpression portal,
                        String iname,
                        String ident,
                        JExpression[] args,
                        SIRLatency latency) {
                    tf[0] = true;
                }
                // above was send, how do we check for receive?
            });
        return tf[0];
    }
    
    /**
     * Check whether a filter's behavior is data dependent (other than through fields).
     * (A peek or pop flows to a branch condition, array offset, or peek offset.)
     * <pre>   
     * push(arr[pop()]);
     * foo = pop();  bar = peek(foo);
     * foo = peek(5); if (foo > 0) baz++;
     * </pre>
     * You can check as to whether there is any possibility of a loop-carried dependency
     * through fields by checking that (! StatelessDuplicate.hasMutableState(f)).
     * <bt/>
     * Currently flow insensitive, context insensitive.
     * @param f : a filter to check.
     * @return true if no data-dependent branches or offsets. 
     */
    public static boolean isDataDependent (SIRFilter f) {
        // For any assignment statement containing a pop or peek:
        // add simplified lhs to list of variables to check.
        // For any variable in loop expression (init, test, stride)
        // or test expression for if, while...  if a r-exp includes
        // a variable tainted by peek or pop, then we have
        // data-dependent branch.
        
        // identifiers in slice of a peek or a pop.
        final Set<String> idents = new HashSet<String>();
        // if peek / pop stored as element of array, we only
        // worry about extracting from array, not about array itself.
        final Set<String> arrayidents = new HashSet<String>();
        // indicator that this pass has found a dependency.
        final boolean[] hasDepend = {false};

        List<JMethodDeclaration> methods = GetSteadyMethods.getSteadyMethods(f);
        int oldIdentSize = -1;
        while (oldIdentSize != idents.size() && !hasDepend[0]) {
            oldIdentSize = idents.size();
            for (JMethodDeclaration method : methods) {
                method.accept(new SLIREmptyVisitor() {
//                  found a peek or pop expression or propagated ident
                    private boolean flowsHere = false;
//                  # surrounding scopes indicating dependence.
                    private int delicateLocation = 0;

                    // peek or pop: if is in a "delicate location" such as
                    // calculating an array offset, initializing a field,
                    // calculating a peek offset, part of a branch condition;
                    // then we have a data dependency already...
                    public void visitPeekExpression(SIRPeekExpression self,
                            CType tapeType, JExpression arg) {
                        flowsHere = true;
                        if (delicateLocation > 0) {
                            hasDepend[0] = true;
                        }
                        delicateLocation++;
                        super.visitPeekExpression(self, tapeType, arg);
                        delicateLocation--;
                    }

                    public void visitPopExpression(SIRPopExpression self,
                            CType tapeType) {
                        flowsHere = true;
                        if (delicateLocation > 0) {
                            hasDepend[0] = true;
                        }
                        delicateLocation++;
                        super.visitPopExpression(self, tapeType);
                        delicateLocation--;
                    }

                    
                    public void visitFieldExpression(JFieldAccessExpression self,
                            JExpression left,
                            String ident)
                    {
                        super.visitFieldExpression(self, left, ident);
                        if (idents.contains(ident)) {
                            if (delicateLocation > 0) {
                                hasDepend[0] = true;
                            }
                            flowsHere = true;
                        }
                    }

                    public void visitNameExpression(JNameExpression self,
                            JExpression prefix,
                            String ident) {
                        super.visitNameExpression(self, prefix, ident);
                        if (idents.contains(ident)) {
                            if (delicateLocation > 0) {
                                hasDepend[0] = true;
                            }
                            flowsHere = true;
                        }
                    }
                    
                    public void visitLocalVariableExpression(JLocalVariableExpression self,
                            String ident) {
                        super.visitLocalVariableExpression(self, ident);
                        if (idents.contains(ident)) {
                            if (delicateLocation > 0) {
                                hasDepend[0] = true;
                            }
                            flowsHere = true;
                        }
                    }
                    
                    public void visitVariableDefinition(
                            JVariableDefinition self, int modifiers,
                            CType type, String ident, JExpression expr) {
                        boolean oldflowsHere = flowsHere;
                        flowsHere = false;
                        super.visitVariableDefinition(self, modifiers, type,
                                ident, expr);
                        if (flowsHere) {
                            idents.add(ident);
                        }
                        flowsHere = oldflowsHere;
                    }

                    // assignment may be to variable or field, in which case track
                    // through idents, or may be into array offset in which case
                    // track in arrayidents which will not pass on taint until
                    // dereferenced.
                    private void checkassign(JExpression left, JExpression right) {
                        left.accept(this);
                        boolean oldflowsHere = flowsHere;
                        flowsHere = false;
                        right.accept(this);
                        if (flowsHere) {
                            Set<String> aid = arrayAccessIn(left);
                            if (! aid.isEmpty() ) {
                                arrayidents.addAll(aid);
                            } else {
                                idents.add((CommonUtils.lhsBaseExpr(left))
                                    .getIdent());
                            }
                        }
                        flowsHere = oldflowsHere;
                    }
                    
                    public void visitAssignmentExpression(
                            JAssignmentExpression self, JExpression left,
                            JExpression right) {
                        checkassign(left,right);
                    }

                    public void visitCompoundAssignmentExpression(
                            JCompoundAssignmentExpression self, int oper,
                            JExpression left, JExpression right) {
                        checkassign(left,right);
                    }

                    public void visitArrayAccessExpression(
                            JArrayAccessExpression self, JExpression prefix,
                            JExpression accessor) {
                        if (arrayidents.contains(CommonUtils.lhsBaseExpr(self).getIdent())) {
                            // dereferencing an array with tainted elements.
                            // not checking for the correct number of levels of dereference...
                            flowsHere = true;
                        }
                        delicateLocation++;
                        super.visitArrayAccessExpression(self, prefix,
                                        accessor);
                        delicateLocation--;
                    }
                    
                    public void visitConditionalExpression(JConditionalExpression self,
                            JExpression cond,
                            JExpression left,
                            JExpression right) {
                        delicateLocation++;
                        cond.accept(this);
                        delicateLocation--;
                        left.accept(this);
                        right.accept(this);
                    }
                    
                    public void visitForStatement(JForStatement self,
                            JStatement init, JExpression cond, JStatement incr,
                            JStatement body) {
                        delicateLocation++;
                        if (init != null) {
                            init.accept(this);
                        }
                        if (cond != null) {
                            cond.accept(this);
                        }
                        if (incr != null) {
                            incr.accept(this);
                        }
                        delicateLocation--;
                        body.accept(this);
                    }

                    public void visitIfStatement(JIfStatement self,
                            JExpression cond, JStatement thenClause,
                            JStatement elseClause) {
                        delicateLocation++;
                        cond.accept(this);
                        delicateLocation--;
                        thenClause.accept(this);
                        if (elseClause != null) {
                            elseClause.accept(this);
                        }
                    }

                    public void visitDoStatement(JDoStatement self,
                            JExpression cond, JStatement body) {
                        body.accept(this);
                        delicateLocation++;
                        cond.accept(this);
                        delicateLocation--;
                    }

                    public void visitWhileStatement(JWhileStatement self,
                            JExpression cond, JStatement body) {
                        delicateLocation++;
                        cond.accept(this);
                        delicateLocation--;
                        body.accept(this);
                    }
                    
                    // assume any call or return with data could
                    // cause a dependence.
                    // since actually have all relevant fns, could
                    // do interprocedural analysis.
                    public void visitArgs(JExpression[] args) {
                        delicateLocation++;
                        super.visitArgs(args);
                        delicateLocation--;
                    }
                    public void visitReturnStatement(JReturnStatement self,
                                     JExpression expr) {
                        delicateLocation++;
                        super.visitReturnStatement(self, expr);
                        delicateLocation--;
                    }
                    
                    /* mostly for checking lhs's. Return names of all arrays accessed in expression. */
                    private Set<String> arrayAccessIn(JExpression e) {
                        final Set<String> s = new HashSet<String>();
                        e.accept(new SLIREmptyVisitor() {
                            public void visitArrayAccessExpression(
                                    JArrayAccessExpression self, JExpression prefix,
                                    JExpression accessor) {
                                s.add(CommonUtils.lhsBaseExpr(prefix).getIdent());
                                super.visitArrayAccessExpression(self, prefix, accessor);
                            }
                        });
                        return s;
                    }
                });
            }
        }
//        // debugging:
        if (hasDepend[0])
    {
        System.err.println("Vectorizable.isDataDependent found idents for " + f.getName() + ":");
        for (String ident : idents) {
            System.err.print(ident + " ");
        }
        System.err.println(hasDepend[0] ? "is data dependent" : "is not data dependent");
    }
        // dependence found during setup.
        if (hasDepend[0]) {
            return true;
        }
        return false;
    }
}