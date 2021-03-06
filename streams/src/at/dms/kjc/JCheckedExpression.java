/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: JCheckedExpression.java,v 1.13 2006-12-20 18:03:32 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.TokenReference;
import at.dms.util.InconsistencyException;

/**
 * An expression that is already analysed.
 */
public final class JCheckedExpression extends JExpression {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected JCheckedExpression() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     */
    public JCheckedExpression(TokenReference where, JExpression checked) {
        super(where);
        this.checked = checked;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Compute the type of this expression (called after parsing)
     * @return the type of this expression
     */
    public CType getType() {
        return checked.getType();
    }
    
    /**
     * Delegates to checked expression.
     */
    public void setType(CType type) {
        checked.setType(type);
    }


    /**
     * Tests whether this expression denotes a compile-time constant (JLS 15.28).
     *
     * @return  true iff this expression is constant
     */
    public boolean isConstant() {
        throw new InconsistencyException("CHECK ME BEFORE AND YOU WONT SEE ME ANYMORE");
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the expression (semantically).
     * @param   context     the analysis context
     * @return  an equivalent, analysed expression
     * @exception   PositionedError the analysis detected an error
     */
    public JExpression analyse(CExpressionContext context) {
        return checked; // already checked
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor
     * @param   p       the visitor
     */
    public void accept(KjcVisitor p) {
        throw new InconsistencyException("CHECK ME BEFORE AND YOU WONT SEE ME ANYMORE");
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    public Object accept(AttributeVisitor p) {
        throw new InconsistencyException("CHECK ME BEFORE AND YOU WONT SEE ME ANYMORE");
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitChecked(this,o);
    }


    /**
     * Generates JVM bytecode to evaluate this expression.
     *
     * @param   code        the bytecode sequence
     * @param   discardValue    discard the result of the evaluation ?
     */
    public void genCode(CodeSequence code, boolean discardValue) {
        throw new InconsistencyException("CHECK ME BEFORE AND YOU WONT SEE ME ANYMORE");
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    private /* final*/ JExpression  checked; // removed final for cloner

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.JCheckedExpression other = new at.dms.kjc.JCheckedExpression();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JCheckedExpression other) {
        super.deepCloneInto(other);
        other.checked = (at.dms.kjc.JExpression)at.dms.kjc.AutoCloner.cloneToplevel(this.checked);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
