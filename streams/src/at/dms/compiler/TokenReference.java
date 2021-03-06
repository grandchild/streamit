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
 * $Id: TokenReference.java,v 1.12 2006-03-24 15:54:46 dimock Exp $
 */

package at.dms.compiler;

import java.io.File;
import java.io.*;
import at.dms.util.Utils;

/**
 * This class defines objets that hold a reference to a file and a position
 * in the file.
 */
public class TokenReference implements Serializable, at.dms.kjc.DeepCloneable {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    private TokenReference() {} // for cloner only

    /**
     * Construct a file and line reference
     * @param   file        the file name
     * @param   line        the line number
     */
    public TokenReference(String file, int line) {
        this.file = file;
        this.line = line;

        last = this;
    }

    /**
     * Construct a line and file reference
     * @param   file        the file name
     * @param   line        the line number
     * WARNING: DOES NOT LIKE MULTITHREADING
     */
    public static TokenReference build(String file, int line) {
        if (line != last.line || file != last.file) {
            return new TokenReference(file, line);
        } else {
            return last;
        }
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns the file name of reference
     */
    public final String getFile() {
        return file;
    }

    /**
     * Returns the name of reference (getFile().baseName())
     */
    public final String getName() {
        String[]    splitted = Utils.splitQualifiedName(file, File.separatorChar);

        return splitted[1];
    }

    /**
     * Returns the line number of reference
     */
    public final int getLine() {
        return line;
    }

    /*
     *
     */
    public String toString() {
        return "[" + file + ":" + line + "]";
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    public static TokenReference    NO_REF = new TokenReference("<GENERATED-BY-KOPI>", 0);

    private static TokenReference   last = NO_REF;

    private /* final */ String      file;  // removed final for cloner
    private /* final */ int     line;  // removed final for cloner

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.compiler.TokenReference other = new at.dms.compiler.TokenReference();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.compiler.TokenReference other) {
        other.file = (java.lang.String)at.dms.kjc.AutoCloner.cloneToplevel(this.file);
        other.line = this.line;
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
