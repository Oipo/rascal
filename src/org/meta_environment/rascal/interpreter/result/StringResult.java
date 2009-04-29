package org.meta_environment.rascal.interpreter.result;

import static org.meta_environment.rascal.interpreter.result.ResultFactory.bool;
import static org.meta_environment.rascal.interpreter.result.ResultFactory.makeResult;

import org.eclipse.imp.pdb.facts.IString;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.type.Type;
import org.meta_environment.rascal.interpreter.EvaluatorContext;

public class StringResult extends ElementResult<IString> {

	private IString string;
	
	public StringResult(Type type, IString string, EvaluatorContext ctx) {
		super(type, string, ctx);
		this.string = string;
	}
	
	@Override
	public IString getValue() {
		return string;
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> add(Result<V> result, EvaluatorContext ctx) {
		return result.addString(this, ctx);
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> compare(Result<V> result, EvaluatorContext ctx) {
		return result.compareString(this, ctx);
	}

	@Override
	public <U extends IValue, V extends IValue> Result<U> equals(Result<V> that, EvaluatorContext ctx) {
		return that.equalToString(this, ctx);
	}

	@Override
	public <U extends IValue, V extends IValue> Result<U> nonEquals(Result<V> that, EvaluatorContext ctx) {
		return that.nonEqualToString(this, ctx);
	}

	@Override
	public <U extends IValue, V extends IValue> Result<U> lessThan(Result<V> result, EvaluatorContext ctx) {
		return result.lessThanString(this, ctx);
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> lessThanOrEqual(Result<V> result, EvaluatorContext ctx) {
		return result.lessThanOrEqualString(this, ctx);
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> greaterThan(Result<V> result, EvaluatorContext ctx) {
		return result.greaterThanString(this, ctx);
	}
	
	@Override
	public <U extends IValue, V extends IValue> Result<U> greaterThanOrEqual(Result<V> result, EvaluatorContext ctx) {
		return result.greaterThanOrEqualString(this, ctx);
	}
	
	//////////////////////
	
	@Override
	protected <U extends IValue> Result<U> addString(StringResult s, EvaluatorContext ctx) {
		// Note the reverse concat.
		return makeResult(type, s.getValue().concat(getValue()), ctx);
	}	
	
	@Override
	protected <U extends IValue> Result<U> compareString(StringResult that, EvaluatorContext ctx) {
		// note reversed args
		IString left = that.getValue();
		IString right = this.getValue();
		int result = left.compare(right);
		return makeIntegerResult(result, ctx);
	}
	
	@Override
	protected <U extends IValue> Result<U> equalToString(StringResult that, EvaluatorContext ctx) {
		return that.equalityBoolean(this, ctx);
	}
	
	@Override
	protected <U extends IValue> Result<U> nonEqualToString(StringResult that, EvaluatorContext ctx) {
		return that.nonEqualityBoolean(this);
	}
	
	@Override
	protected <U extends IValue> Result<U> lessThanString(StringResult that, EvaluatorContext ctx) {
		// note reversed args: we need that < this
		return bool(that.comparisonInts(this, ctx) < 0);
	}
	
	@Override
	protected <U extends IValue> Result<U> lessThanOrEqualString(StringResult that, EvaluatorContext ctx) {
		// note reversed args: we need that <= this
		return bool(that.comparisonInts(this, ctx) <= 0);
	}

	@Override
	protected <U extends IValue> Result<U> greaterThanString(StringResult that, EvaluatorContext ctx) {
		// note reversed args: we need that > this
		return bool(that.comparisonInts(this, ctx) > 0);
	}
	
	@Override
	protected <U extends IValue> Result<U> greaterThanOrEqualString(StringResult that, EvaluatorContext ctx) {
		// note reversed args: we need that >= this
		return bool(that.comparisonInts(this, ctx) >= 0);
	}

	
}
