@license{
  Copyright (c) 2009-2011 CWI
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
@contributor{Mark Hills - Mark.Hills@cwi.nl (CWI)}
@bootstrapParser
module lang::rascal::checker::constraints::Visit

import List;
import ParseTree;
import lang::rascal::types::Types;
import lang::rascal::scoping::SymbolTable;
import lang::rascal::checker::constraints::Constraints;
import lang::rascal::syntax::RascalRascal;

//
// Gather constraints over the visit (statement/expression).
//
// TODO: Add type rule!
//
public ConstraintBase gatherVisitConstraints(STBuilder stBuilder, ConstraintBase constraintBase, Visit v) {
    if (`visit (<Expression se>) { <Case+ cs> }` := v || `<Strategy st> visit (<Expression se>) { <Case+ cs> }` := v) {
        <constraintBase, t1> = makeFreshType(constraintBase);
        
        // Step 1: The expression is of arbitrary type
        constraintBase.constraints = constraintBase.constraints + TreeIsType(se,se@\loc,t1);
        
        // Step 2: The result of the visit is of the same type as the visited expression
        constraintBase.constraints = constraintBase.constraints + TreeIsType(v,v@\loc,t1);
        
        // Step 3: Each case should be a case type, like in a switch, and the case pattern
        // should indicate a pattern which can be bound to something reachable from e.
        // TODO: This check may not be complete! We need to verify that this actually
        // always works.
        for (c <- cs) {
            <constraintBase, t2> = makeFreshType(constraintBase);
            Constraint c1 = TreeIsType(c,c@\loc,t2);
            Constraint c2 = CaseIsReachable(t2,t1,c@\loc);
            constraintBase.constraints = constraintBase.constraints + { c1, c2 };
        }
    }
    
    return constraintBase;
}
