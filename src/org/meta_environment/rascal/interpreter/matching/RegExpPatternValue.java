package org.meta_environment.rascal.interpreter.matching;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IString;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.type.Type;
import org.eclipse.imp.pdb.facts.type.TypeFactory;
import org.meta_environment.rascal.ast.AbstractAST;
import org.meta_environment.rascal.interpreter.EvaluatorContext;
import org.meta_environment.rascal.interpreter.asserts.NotYetImplemented;
import org.meta_environment.rascal.interpreter.env.Environment;
import org.meta_environment.rascal.interpreter.result.Result;
import org.meta_environment.rascal.interpreter.staticErrors.SyntaxError;
import org.meta_environment.rascal.interpreter.staticErrors.UnexpectedTypeError;

import static org.meta_environment.rascal.interpreter.result.ResultFactory.makeResult;

public class RegExpPatternValue implements MatchPattern {
	private AbstractAST ast;					// The AST for this regexp
	private String RegExpAsString;				// The regexp represented as string
	//private Character modifier;				// Optional modifier following the pattern
	private Pattern pat;						// The Pattern resulting from compiling the regexp

	private List<String> patternVars;			// The variables occurring in the regexp
	private HashMap<String, String> boundBeforeConstruction = new HashMap<String, String>();
												// The variable (and their value) that were already bound 
												// when the  pattern was constructed
	private Matcher matcher;					// The actual regexp matcher
	String subject;								// Subject string to be matched
	private boolean initialized = false;		// Has matcher been initialized?
	private boolean firstMatch;				// Is this the first match?
	private boolean hasNext;					// Are there more matches?
	
	private int start;							// start of last match in current subject
	private int end;							// end of last match in current subject
	
	private final Environment env;
	private final TypeFactory tf = TypeFactory.getInstance();
	private final IValueFactory vf;
	private EvaluatorContext context;
	
	RegExpPatternValue(IValueFactory vf, EvaluatorContext context, String s, Environment env){
		this.context = context;
		RegExpAsString = s;
	//	modifier = null;
		patternVars = null;
		initialized = false;
		this.env = env;
		this.vf = vf;
	}
	
	RegExpPatternValue(IValueFactory vf, AbstractAST ast, String s, Character mod, List<String> names, Environment env){
		this.ast = ast;
		RegExpAsString = (mod == null) ? s : "(?" + mod + ")" + s;
		patternVars = names;
		initialized = false;
		for(String name : names){
			Result<IValue> localRes = env.getLocalVariable(name);
			if(localRes != null){
				if(!localRes.getType().isStringType()){
					throw new UnexpectedTypeError(tf.stringType(), localRes.getType(), ast);
				}
				if(localRes.getValue() != null){
					boundBeforeConstruction.put(name, ((IString)localRes.getValue()).getValue());
				} else {
					// Introduce an innermost variable that shadows the original one.
					// This ensures that the original one becomes undefined again when matching is over
					env.storeInnermostVariable(name, makeResult(localRes.getType(), null, context));
				}
				continue;
			}	
			Result<IValue> globalRes = env.getVariable(ast, name);
			if(globalRes != null){
				if(!globalRes.getType().isStringType()){
					throw new UnexpectedTypeError(tf.stringType(), globalRes.getType(), ast);
				}
				if(globalRes.getValue() != null){
					boundBeforeConstruction.put(name, ((IString)globalRes.getValue()).getValue());	
				} else {
					// Introduce an innermost variable that shadows the original one.
					// This ensures that the original one becomes undefined agaian when matching is over
					env.storeInnermostVariable(name, makeResult(globalRes.getType(), null, context));

				}
				continue;
			}
			env.storeInnermostVariable(name, null);
		}
		this.env = env;
		this.vf = vf;
	}
	
	public Type getType(Environment ev) {
		return tf.stringType();
	}

	public void initMatch(IValue subject, Environment ev) {
		if(!subject.getType().isStringType()){
			hasNext = false;
			return;
		}
		this.subject = ((IString) subject).getValue();
		initialized = firstMatch = hasNext = true;
	
		try {
			pat = Pattern.compile(RegExpAsString);
		} catch (PatternSyntaxException e){
			ISourceLocation loc = ast.getLocation();
			throw new SyntaxError(e.getMessage(), loc);
		}
	}
	
	public boolean hasNext() {
		return initialized && (firstMatch || hasNext);
	}
	
	public boolean mayMatch(Type subjectType, Environment env) {
		return subjectType.equivalent(tf.stringType());
	}
	
	public int getStart(){
		return start;
	}
	
	public int getEnd(){
		return end;
	}
	
	private boolean findMatch(){
		
		while(matcher.find()){
			boolean matches = true;
			Map<String,String> bindings = getBindings();
			for(String name : bindings.keySet()){
				String valBefore = boundBeforeConstruction.get(name);
				if(valBefore != null){
					if(!valBefore.equals(bindings.get(name))){
						matches = false;
						break;
					}
				}
				/*
				 * Note that regular expressions cannot be non-linear, e.g. duplicate occurrences 
				 * of variables are not allowed. Otherwise we would have to check here for the
				 * previous local value of the variable.
				 */
				env.storeVariable(name, makeResult(tf.stringType(), vf.string(bindings.get(name)), context));			
			}
			if(matches){
				start = matcher.start();
				end = matcher.end();
				return true;
			}
		}
		hasNext = false;
		start = end = -1;
		return false;
	}
	
	public boolean next(){
		if(firstMatch){
			firstMatch = false;
			matcher = pat.matcher(subject);
		}
		return findMatch();
	}
	
	public java.util.List<String> getVariables(){
		return patternVars;
	}
	
	private Map<String,String> getBindings(){
		Map<String,String> bindings = new HashMap<String,String>();
		int k = 1;
		for(String nm : patternVars){
			bindings.put(nm, matcher.group(k));
			k++;
		}
		return bindings;
	}
	
	@Override
	public String toString(){
		return "RegExpPatternValue(" + RegExpAsString + ", " + patternVars + ")";
	}

	public IValue toIValue(Environment env) {
		// TODO implement
		throw new NotYetImplemented(ast);
	}

	public AbstractAST getAST() {
		return ast;
	}
}
