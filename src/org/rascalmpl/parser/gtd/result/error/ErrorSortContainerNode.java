/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:

 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
*******************************************************************************/
package org.rascalmpl.parser.gtd.result.error;

import java.net.URI;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IListWriter;
import org.eclipse.imp.pdb.facts.ISetWriter;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.rascalmpl.parser.gtd.result.AbstractContainerNode;
import org.rascalmpl.parser.gtd.result.AbstractNode;
import org.rascalmpl.parser.gtd.result.action.IActionExecutor;
import org.rascalmpl.parser.gtd.result.struct.Link;
import org.rascalmpl.parser.gtd.util.ArrayList;
import org.rascalmpl.parser.gtd.util.DoubleArrayList;
import org.rascalmpl.parser.gtd.util.IndexedStack;
import org.rascalmpl.parser.gtd.util.specific.PositionStore;
import org.rascalmpl.values.uptr.Factory;
import org.rascalmpl.values.uptr.ProductionAdapter;

public class ErrorSortContainerNode extends AbstractContainerNode{
	private IList unmatchedInput; // TODO Fix one time action execution for filtered nodes.
	
	private IConstructor cachedResult;
	
	public ErrorSortContainerNode(URI input, int offset, int endOffset, boolean isSeparator, boolean isLayout){
		super(input, offset, endOffset, false, isSeparator, isLayout);
		
		this.unmatchedInput = EMPTY_LIST;
	}
	
	public void setUnmatchedInput(IList unmatchedInput){
		this.unmatchedInput = unmatchedInput;
	}
	
	protected static void gatherAlternatives(Link child, DoubleArrayList<IConstructor[], IConstructor> gatheredAlternatives, IConstructor production, IndexedStack<AbstractNode> stack, int depth, CycleMark cycleMark, PositionStore positionStore, IActionExecutor actionExecutor){
		AbstractNode resultNode = child.node;
		
		if(!(resultNode.isEpsilon() && child.prefixes == null)){
			AbstractNode[] postFix = new AbstractNode[]{resultNode};
			gatherProduction(child, postFix, gatheredAlternatives, production, stack, depth, cycleMark, positionStore, actionExecutor);
		}else{
			gatheredAlternatives.add(new IConstructor[]{}, production);
		}
	}
	
	private static void gatherProduction(Link child, AbstractNode[] postFix, DoubleArrayList<IConstructor[], IConstructor> gatheredAlternatives, IConstructor production, IndexedStack<AbstractNode> stack, int depth, CycleMark cycleMark, PositionStore positionStore, IActionExecutor actionExecutor){
		ArrayList<Link> prefixes = child.prefixes;
		if(prefixes == null){
			int postFixLength = postFix.length;
			IConstructor[] constructedPostFix = new IConstructor[postFixLength];
			for(int i = 0; i < postFixLength; ++i){
				IConstructor node = postFix[i].toErrorTree(stack, depth, cycleMark, positionStore, actionExecutor);
				if(node == null) return;
				constructedPostFix[i] = node;
			}
			
			gatheredAlternatives.add(constructedPostFix, production);
			return;
		}
		
		for(int i = prefixes.size() - 1; i >= 0; --i){
			Link prefix = prefixes.get(i);
			
			AbstractNode resultNode = prefix.node;
			if(!resultNode.isRejected()){
				int length = postFix.length;
				AbstractNode[] newPostFix = new AbstractNode[length + 1];
				System.arraycopy(postFix, 0, newPostFix, 1, length);
				newPostFix[0] = resultNode;
				gatherProduction(prefix, newPostFix, gatheredAlternatives, production, stack, depth, cycleMark, positionStore, actionExecutor);
			}
		}
	}
	
	private IConstructor buildAlternative(IConstructor production, IValue[] children){
		IListWriter childrenListWriter = VF.listWriter(Factory.Tree);
		for(int i = children.length - 1; i >= 0; --i){
			childrenListWriter.insert(children[i]);
		}
		
		return VF.constructor(Factory.Tree_Error, production, childrenListWriter.done(), unmatchedInput);
	}
	
	public IConstructor toTree(IndexedStack<AbstractNode> stack, int depth, CycleMark cycleMark, PositionStore positionStore, FilteringTracker filteringTracker, IActionExecutor actionExecutor){
		throw new UnsupportedOperationException("This type of node can only build error trees.");
	}
	
	public IConstructor toErrorTree(IndexedStack<AbstractNode> stack, int depth, CycleMark cycleMark, PositionStore positionStore, IActionExecutor actionExecutor){
		if(depth <= cycleMark.depth){
			if(cachedResult != null){
				if(cachedResult.getConstructorType() != FILTERED_RESULT_TYPE){
					return cachedResult;
				}
				IValue filteredTree = cachedResult.get(0);
				if(filteredTree instanceof IConstructor){
					return (IConstructor) filteredTree;
				}
			}
			
			cycleMark.reset();
		}
		
		if(rejected){
			// TODO Handle filtering.
			cachedResult = FILTERED_RESULT;
			return null;
		}
		
		ISourceLocation sourceLocation = null;
		if(!(isLayout || input == null)){
			int beginLine = positionStore.findLine(offset);
			int endLine = positionStore.findLine(endOffset);
			sourceLocation = VF.sourceLocation(input, offset, endOffset - offset, beginLine + 1, endLine + 1, positionStore.getColumn(offset, beginLine), positionStore.getColumn(endOffset, endLine));
		}
		
		int index = stack.contains(this);
		if(index != -1){ // Cycle found.
			IConstructor cycle = VF.constructor(Factory.Tree_Cycle, ProductionAdapter.getRhs(firstProduction), VF.integer(depth - index));
			cycle = actionExecutor.filterCycle(cycle);
			if(cycle != null && sourceLocation != null) cycle = cycle.setAnnotation(Factory.Location, sourceLocation);
			
			cycleMark.setMark(index);
			
			return cycle;
		}
		
		int childDepth = depth + 1;
		
		stack.push(this, depth); // Push.
		
		// Gather
		DoubleArrayList<IConstructor[], IConstructor> gatheredAlternatives = new DoubleArrayList<IConstructor[], IConstructor>();
		gatherAlternatives(firstAlternative, gatheredAlternatives, firstProduction, stack, childDepth, cycleMark, positionStore, actionExecutor);
		if(alternatives != null){
			for(int i = alternatives.size() - 1; i >= 0; --i){
				gatherAlternatives(alternatives.get(i), gatheredAlternatives, productions.get(i), stack, childDepth, cycleMark, positionStore, actionExecutor);
			}
		}
		
		// Output.
		IConstructor result = null;
		
		int nrOfAlternatives = gatheredAlternatives.size();
		if(nrOfAlternatives == 1){ // Not ambiguous.
			IConstructor production = gatheredAlternatives.getSecond(0);
			IValue[] alternative = gatheredAlternatives.getFirst(0);
			result = buildAlternative(production, alternative);
			result = actionExecutor.filterProduction(result);
			if(result != null){
				if(sourceLocation != null) result = result.setAnnotation(Factory.Location, sourceLocation);
			}else{
				// TODO Handle filtering.
			}
		}else if(nrOfAlternatives > 0){ // Ambiguous.
			ISetWriter ambSetWriter = VF.setWriter(Factory.Tree);
			IConstructor lastAlternative = null;
			
			for(int i = nrOfAlternatives - 1; i >= 0; --i){
				IConstructor production = gatheredAlternatives.getSecond(i);
				IValue[] alternative = gatheredAlternatives.getFirst(i);
				
				IConstructor alt = buildAlternative(production, alternative);
				alt = actionExecutor.filterProduction(alt);
				if(alt != null){
					if(sourceLocation != null) alt = alt.setAnnotation(Factory.Location, sourceLocation);
					lastAlternative = alt;
					ambSetWriter.insert(alt);
				}
			}
			
			if(ambSetWriter.size() == 1){
				result = lastAlternative;
			}else if(ambSetWriter.size() == 0){
				// TODO Handle filtering.
			}else{
				result = VF.constructor(Factory.Tree_Amb, ambSetWriter.done());
				result = actionExecutor.filterAmbiguity(result);
				if(result == null){
					cachedResult = FILTERED_RESULT;
					return null;
				}
				
				if(sourceLocation != null) result = result.setAnnotation(Factory.Location, sourceLocation);
			}
		}
		
		stack.dirtyPurge(); // Pop.
		
		if(result == null){
			cachedResult = FILTERED_RESULT;
		}else if(depth < cycleMark.depth){
			cachedResult = result;
		}
		
		return result;
	}
}
