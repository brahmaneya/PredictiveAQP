package solvers;

import static java.lang.System.out;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import utils.PerformanceAnalysis;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;
import com.joptimizer.optimizers.OptimizationResponse;

public class ProbabilisticSolvers {
	
	/**
	 * sizesKnownEvaluate is like biGreedyEvaluate, except that it takes variance into account on the RHS, and thus
	 * both constraints are satisfied with probability rho, not just in expectation.
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param selectivities		Maps tuple class to selectivity of tuples in class.
	 * @param alpha				Precision constraint.
	 * @param beta				Recall constraint.
	 * @param rho				Probability constraint for achieving given precision/recall.
	 * @param retrieve			Used to return the retrieval probability of tuple class.
	 * @param evaluate			Used to return the evaluation probability of tuple class.
	 */
	public static void sizesKnownEvaluate(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Map<String, Double> retrieve, Map<String, Double> evaluate) {
		// Sorting keys in decreasing order of selectivity.
		List<String> keyList = new ArrayList<String>(selectivities.keySet());
		final Map<String, Double> fSelectivities = selectivities;
		Collections.sort(keyList, new Comparator<String>(){
			@Override
			public int compare(String arg0, String arg1) {
				final Double val0 = fSelectivities.get(arg0);
				final Double val1 = fSelectivities.get(arg1);
				if (val0 > val1) {
					return -1;
				} else if (val0.equals(val1)) {
					return 0;
				} else {
					return 1;
				}
			}
		});
	
		// Computing the rhs of the recall and precision constraints.
		Double recallRHSExpectation = 0.0;
		Double recallRHSBuffer = 0.0; // This is how much we need to exceed the expectation to achieve the probabilistic guarantee.
		Double precisionRHSBuffer = 0.0;
		
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			final Double ca = size * selectivity;
			final Double wa = size * (1 - selectivity);
			
			recallRHSExpectation  += beta * ca;
			recallRHSBuffer += ca / 2;
			
			precisionRHSBuffer += (ca * (1 - alpha) * (1 - alpha) + wa * alpha * alpha) / 2;
		}
		
		recallRHSBuffer *= -Math.log(1 - rho);
		recallRHSBuffer = Math.sqrt(recallRHSBuffer);
		Double recallRHS = recallRHSExpectation + recallRHSBuffer;
		
		precisionRHSBuffer *= -Math.log(1 - rho);
		precisionRHSBuffer = Math.sqrt(precisionRHSBuffer);
		Double precisionRHS = 0.0 + precisionRHSBuffer;

		// Greedily assigning retrieves, while evaluating precision constraint rhs.
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			if (recallRHS <= 0.0) {
				final Double r = 0.0;
				retrieve.put(key, r);
			} else if (size*selectivity >= recallRHS) {
				final Double r = recallRHS/(size*selectivity);
				retrieve.put(key, r);
				precisionRHS -= r*size*(selectivity - alpha);
				recallRHS = 0.0;
			} else {
				final Double r = 1.0;
				retrieve.put(key, r);
				precisionRHS -= r*size*(selectivity - alpha);
				recallRHS -= size*selectivity;
			}
		}
		
		// Greedily assigning evaluates, in increasing selectivity order.
		Collections.reverse(keyList);
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			final Double r = retrieve.get(key);
			if (r.equals(0.0)) {
				final Double e = 0.0;
				evaluate.put(key, e);
			} else if (precisionRHS <= 0) {
				final Double e = 0.0;
				evaluate.put(key, e);
			} else if (precisionRHS <= r*alpha*(1-selectivity)*size) {
				final Double e = precisionRHS/(alpha*(1-selectivity)*size);
				evaluate.put(key, e);
				precisionRHS = 0.0;
			} else {
				final Double e = r;
				evaluate.put(key, e);
				precisionRHS -= e*size*alpha*(1-selectivity);
			}
		}
	}
	
	/*
	 * The input variables to the function are R_a's, then E_a's and then F_a's. The F_a's are to be interpreted
	 * as tuples to be sampled in addition to ones already sampled (Fc[a], Fw[a]). Thus t_a has the already-sampled
	 * tuples pre-subtracted from it, but we still have to subtract F_a from it in the equations.
	 * To convexify the function, (R_a-E_a\alpha)^2 is replaced by 1. 
	 * Function is: 
	 * c_{\rho} \sqrt{\sum_{a\in A} t_a^2 \frac{s_a(1-s_a)}{Fc_a+Fw_a+F_a+3} + 0.25t_a}
	 * - \sum_{a \in A} (1-\alpha)(t_a-F_a)R_as_a + (1-\alpha)(Fc_a+F_as_a) - (t_a-F_a)\alpha(R_a-E_a)(1-s_a)
	 
	private static class GeneralPrecisionConstraintFunction implements ConvexMultivariateRealFunction {

		int numGroups;
		double alpha;
		double c_rho;
		double[] t;
		double[] s;
		double[] v;
		int[] Fc;
		int[] Fw;
		
		@SuppressWarnings("unused")
		private GeneralPrecisionConstraintFunction () {
			// Empty constructor to prevent instantiation.
		}
		
		public GeneralPrecisionConstraintFunction (double alpha, double rho, Map<String, Double> sizes, Map<String, Double> selectivities, 
				Map<String, Double> variances, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
			numGroups = sizes.size();
			this.alpha = alpha;
			this.c_rho = Math.sqrt(1/(1 - rho)); //Math.log(1/(1-rho)/2); //works wayyy better, almost exact if not for the loosening under square root.
			t = new double[numGroups];
			s = new double[numGroups];
			v = new double[numGroups];
			Fc = new int[numGroups];
			Fw = new int[numGroups];
			int a = 0;
			for (String key : sizes.keySet()) {
				t[a] = sizes.get(key);
				if (positiveSamples != null) {
					Fc[a] = positiveSamples.get(key);
					Fw[a] = negativeSamples.get(key);
				} else {
					Fc[a] = 0;
					Fw[a] = 0;	
				}
				if (selectivities == null) {
					s[a] = (Fc[a] + 1.0) / (Fc[a] + Fw[a] + 3);
				} else {
					s[a] = selectivities.get(key);
				}
				if (variances == null) {
					v[a] = s[a] * (1-s[a]) / (Fc[a] + Fw[a] + 3);
				} else {
					v[a] = variances.get(key);
				}
				a++;
			}
		}
		
		@Override
		public double value(double[] X) {
			Double value = 0.0;
			for (int a = 0; a < numGroups; a++) {
				value += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				value += 0.25 * t[a];
			}
			value = c_rho * Math.sqrt(value);
			for (int a = 0; a < numGroups; a++) {
				value -= (1 - alpha) * (Fc[a] + X[a + 2 * numGroups] * s[a]) + (1 - alpha) * (t[a] - X[a + 2 * numGroups]) * X[a] * s[a] - (t[a] - X[a + 2 * numGroups]) * alpha * (X[a] - X[a + numGroups]) * (1 - s[a]);
			}
			return value;
		}

		@Override
		public double[] gradient(double[] X) {
			double[] grad = new double[2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a = 0; a < numGroups; a++) {
				grad[a] = -t[a] * (s[a] - alpha);
				grad[a + numGroups] = -t[a] * alpha * (1 - s[a]);
				grad[a + 2 * numGroups] = -c_rho * t[a] * t[a] * s[a] * (1-s[a]) / ((Fc[a] + Fw[a] + X[a + 2 * numGroups]) *
						(Fc[a] + Fw[a] + X[a + 2 * numGroups]) * 2 * var);
				grad[a + 2 * numGroups] -= s[a] * (1 - alpha);
			}
			return grad;
		}

		@Override
		public double[][] hessian(double[] X) {
			double[][] hess = new double[3 * numGroups][3 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a1 = 0; a1 < numGroups; a1++) {
				hess[a1 + 2 * numGroups][a1 + 2 * numGroups] = c_rho * t[a1] * t[a1] * s[a1] * (1-s[a1]) / ((Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) *
						(Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * (Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * var);
				for (int a2 = 0; a2 < numGroups; a2++) {
					hess[a1 + 2 * numGroups][a2 + 2 * numGroups] -= c_rho 
							* (t[a1] * t[a1] * s[a1] * (1-s[a1]) / ((Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * (Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]))) 
							* (t[a2] * t[a2] * s[a2] * (1-s[a2]) / ((Fc[a2] + Fw[a2] + X[a2 + 2 * numGroups]) * (Fc[a2] + Fw[a2] + X[a2 + 2 * numGroups]))) 
							/ (4 * var * var * var);
				}	
			}
			return hess;
		}

		@Override
		public int getDim() {
			return 3 * numGroups;
		}
	}
	*/
	
	/*
	 * The input variables to the function are R_a's, then E_a's and then F_a's. The F_a's are to be interpreted
	 * as tuples to be sampled in addition to ones already sampled (Fc[a], Fw[a]). Thus t_a has the already-sampled
	 * tuples pre-subtracted from it, but we still have to subtract F_a from it in the equations.
	 * To convexify the function, (R_a-\beta)^2 is replaced by 1. 
	 * Function is: 
	 * c_{\rho} \sqrt{\sum_{a\in A} t_a^2 \frac{s_a(1-s_a)}{Fc_a+Fw_a+F_a+3} + 0.25t_a}
	 * - \sum_{a \in A} Fc_a + (t_a - F_a) R_as_a + F_as_a - t_a s_a\beta - Fc_a\beta
	 
	private static class GeneralRecallConstraintFunction implements ConvexMultivariateRealFunction {

		int numGroups;
		double alpha;
		double c_rho;
		double[] t;
		double[] s;
		double[] v;
		int[] Fc;
		int[] Fw;
		
		@SuppressWarnings("unused")
		private GeneralRecallConstraintFunction () {
			// Empty constructor to prevent instantiation.
		}
		
		public GeneralRecallConstraintFunction (double alpha, double rho, Map<String, Double> sizes, Map<String, Double> selectivities, 
				Map<String, Double> variances, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
			numGroups = sizes.size();
			this.alpha = alpha;
			this.c_rho = Math.sqrt(1/(1 - rho)); //Math.log(1/(1-rho)/2); //works wayyy better, almost exact if not for the loosening under square root.
			t = new double[numGroups];
			s = new double[numGroups];
			v = new double[numGroups];
			Fc = new int[numGroups];
			Fw = new int[numGroups];
			int a = 0;
			for (String key : sizes.keySet()) {
				t[a] = sizes.get(key);
				if (positiveSamples != null) {
					Fc[a] = positiveSamples.get(key);
					Fw[a] = negativeSamples.get(key);
				} else {
					Fc[a] = 0;
					Fw[a] = 0;	
				}
				if (selectivities == null) {
					s[a] = (Fc[a] + 1.0) / (Fc[a] + Fw[a] + 3);
				} else {
					s[a] = selectivities.get(key);
				}
				if (variances == null) {
					v[a] = s[a] * (1-s[a]) / (Fc[a] + Fw[a] + 3);
				} else {
					v[a] = variances.get(key);
				}
				a++;
			}
		}
		
		@Override
		public double value(double[] X) {
			Double value = 0.0;
			for (int a = 0; a < numGroups; a++) {
				value += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				value += 0.25 * t[a];
			}
			value = c_rho * Math.sqrt(value);
			for (int a = 0; a < numGroups; a++) {
				value -= (1 - alpha) * (Fc[a] + X[a + 2 * numGroups] * s[a]) + (1 - alpha) * t[a] * X[a] * s[a] - t[a] * alpha * (X[a] - X[a + numGroups]) * (1 - s[a]);
			}
			return value;
		}

		@Override
		public double[] gradient(double[] X) {
			double[] grad = new double[2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a = 0; a < numGroups; a++) {
				grad[a] = -t[a] * (s[a] - alpha);
				grad[a + numGroups] = -t[a] * alpha * (1 - s[a]);
				grad[a + 2 * numGroups] = -c_rho * t[a] * t[a] * s[a] * (1-s[a]) / ((Fc[a] + Fw[a] + X[a + 2 * numGroups]) *
						(Fc[a] + Fw[a] + X[a + 2 * numGroups]) * 2 * var);
				grad[a + 2 * numGroups] -= s[a] * (1 - alpha);
			}
			return grad;
		}

		@Override
		public double[][] hessian(double[] X) {
			double[][] hess = new double[3 * numGroups][3 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * s[a] * (1-s[a]) / (Fc[a] + Fw[a] + X[a + 2 * numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a1 = 0; a1 < numGroups; a1++) {
				hess[a1 + 2 * numGroups][a1 + 2 * numGroups] = c_rho * t[a1] * t[a1] * s[a1] * (1-s[a1]) / ((Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) *
						(Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * (Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * var);
				for (int a2 = 0; a2 < numGroups; a2++) {
					hess[a1 + 2 * numGroups][a2 + 2 * numGroups] -= c_rho 
							* (t[a1] * t[a1] * s[a1] * (1-s[a1]) / ((Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]) * (Fc[a1] + Fw[a1] + X[a1 + 2 * numGroups]))) 
							* (t[a2] * t[a2] * s[a2] * (1-s[a2]) / ((Fc[a2] + Fw[a2] + X[a2 + 2 * numGroups]) * (Fc[a2] + Fw[a2] + X[a2 + 2 * numGroups]))) 
							/ (4 * var * var * var);
				}	
			}
			return hess;
		}

		@Override
		public int getDim() {
			return 3 * numGroups;
		}
	}
	*/
	
	/*
	 * The input variables to the function are R_a's and then E_a's. 
	 * Function is: 
	 * c_{\rho} \sqrt{\sum_{a\in A} t_a^2v_a(R_a-\alpha E_a)^2 + 0.25t_a}
	 * - \sum_{a \in A} (1-\alpha)Fc_a + (1-\alpha)t_aR_as_a - t_a\alpha(R_a-E_a)(1-s_a)	 
	 */
	private static class PrecisionConstraintFunction implements ConvexMultivariateRealFunction {

		int numGroups;
		double alpha;
		double c_rho;
		double[] t;
		double[] s;
		double[] v;
		int[] Fc;
		int[] Fw;
		
		@SuppressWarnings("unused")
		private PrecisionConstraintFunction () {
			// Empty constructor to prevent instantiation.
		}
		
		/* 
		 * positiveSamples and negativeSamples are optional, but must be non-null if either of
		 * selectivities and variances is null. If selectivities is null, the sample numbers will be used to
		 * find s_a, similarly for v_a.
		 */
		public PrecisionConstraintFunction (double alpha, double rho, Map<String, Double> sizes, Map<String, Double> selectivities, 
				Map<String, Double> variances, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
			numGroups = sizes.size();
			this.alpha = alpha;
			this.c_rho = Math.sqrt(1/(1 - rho)); //Math.log(1/(1-rho)/2); //works wayyy better, almost exact if not for the loosening under square root.
			t = new double[numGroups];
			s = new double[numGroups];
			v = new double[numGroups];
			Fc = new int[numGroups];
			Fw = new int[numGroups];
			int a = 0;
			for (String key : sizes.keySet()) {
				t[a] = sizes.get(key);
				if (positiveSamples != null) {
					Fc[a] = positiveSamples.get(key);
					Fw[a] = negativeSamples.get(key);
				} else {
					Fc[a] = 0;
					Fw[a] = 0;	
				}
				if (selectivities == null) {
					s[a] = (Fc[a] + 1.0) / (Fc[a] + Fw[a] + 3);
				} else {
					s[a] = selectivities.get(key);
				}
				if (variances == null) {
					v[a] = s[a] * (1-s[a]) / (Fc[a] + Fw[a] + 3);
				} else {
					v[a] = variances.get(key);
				}
				a++;
			}
		}
		
		@Override
		public double value(double[] X) {
			Double value = 0.0;
			for (int a = 0; a < numGroups; a++) {
				value += t[a] * t[a] * v[a] * (X[a] - alpha * X[a + numGroups]) * (X[a] - alpha * X[a + numGroups]);
				value += 0.25 * t[a];
			}
			value = c_rho * Math.sqrt(value);
			for (int a = 0; a < numGroups; a++) {
				value -= (1 - alpha) * Fc[a] + (1 - alpha) * t[a] * X[a] * s[a] - t[a] * alpha * (X[a] - X[a + numGroups]) * (1 - s[a]);
			}
			return value;
		}

		@Override
		public double[] gradient(double[] X) {
			double[] grad = new double[2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * v[a] * (X[a] - alpha * X[a + numGroups]) * (X[a] - alpha * X[a + numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a = 0; a < numGroups; a++) {
				grad[a] = c_rho * t[a] * t[a] * v[a] * (X[a] - alpha * X[a + numGroups]) / var;
				grad[a] -= t[a] * (s[a] - alpha);
				grad[a + numGroups] = -alpha * c_rho * t[a] * t[a] * v[a] * (X[a] - alpha * X[a + numGroups]) / var;
				grad[a + numGroups] -= t[a] * alpha * (1 - s[a]);
			}
			return grad;
		}

		@Override
		public double[][] hessian(double[] X) {
			double[][] hess = new double[2 * numGroups][2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * v[a] * (X[a] - alpha * X[a + numGroups]) * (X[a] - alpha * X[a + numGroups]);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a1 = 0; a1 < numGroups; a1++) {
				hess[a1][a1] = c_rho * t[a1] * t[a1] * v[a1] / var;
				hess[a1][a1 + numGroups] = -alpha * c_rho * t[a1] * t[a1] * v[a1] / var;
				hess[a1 + numGroups][a1] = -alpha * c_rho * t[a1] * t[a1] * v[a1] / var;
				hess[a1 + numGroups][a1 + numGroups] = alpha * alpha * c_rho * t[a1] * t[a1] * v[a1] / var;
				for (int a2 = 0; a2 < numGroups; a2++) {
					hess[a1][a2] -= c_rho * (t[a1] * t[a1] * v[a1] * (X[a1] - alpha * X[a1 + numGroups])) 
							* (t[a2] * t[a2] * v[a2] * (X[a2] - alpha * X[a2 + numGroups])) / (var * var * var);
					hess[a1][a2 + numGroups] += alpha * c_rho * (t[a1] * t[a1] * v[a1] * (X[a1] - alpha * X[a1 + numGroups])) 
							* (t[a2] * t[a2] * v[a2] * (X[a2] - alpha * X[a2 + numGroups])) / (var * var * var);
					hess[a1 + numGroups][a2] += alpha * c_rho * (t[a1] * t[a1] * v[a1] * (X[a1] - alpha * X[a1 + numGroups])) 
							* (t[a2] * t[a2] * v[a2] * (X[a2] - alpha * X[a2 + numGroups])) / (var * var * var);
					hess[a1 + numGroups][a2 + numGroups] -= alpha * alpha * c_rho * (t[a1] * t[a1] * v[a1] * (X[a1] - alpha * X[a1 + numGroups])) 
							* (t[a2] * t[a2] * v[a2] * (X[a2] - alpha * X[a2 + numGroups])) / (var * var * var);
				}	
			}
			return hess;
		}

		@Override
		public int getDim() {
			return 2 * numGroups;
		}
	}

	/*
	 * The input variables to the function are R_a's and then E_a's. 
	 * Function is: 
	 * c_{\rho} \sqrt{\sum_{a\in A} t_a^2v_a(R_a-beta)^2 + 0.25t_a}
	 * - \sum_{a \in A} Fc_a + t_aR_as_a - t_as_a\beta - Fc_a\beta
	 */
	private static class RecallConstraintFunction implements ConvexMultivariateRealFunction {

		int numGroups;
		double beta;
		double c_rho;
		double[] t;
		double[] s;
		double[] v;
		int[] Fc;
		int[] Fw;
		
		@SuppressWarnings("unused")
		private RecallConstraintFunction () {
			// Empty constructor to prevent instantiation.
		}
		
		/* 
		 * positiveSamples and negativeSamples are optional, but must be non-null if either of
		 * selectivities and variances is null. If selectivities is null, the sample numbers will be used to
		 * find s_a, similarly for v_a.
		 */
		public RecallConstraintFunction (double beta, double rho, Map<String, Double> sizes, Map<String, Double> selectivities, 
				Map<String, Double> variances, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
			numGroups = sizes.size();
			this.beta = beta;
			this.c_rho = Math.log(1/(1-rho)); //Math.sqrt(1/(1 - rho)); //works wayyy better, almost exact if not for the loosening under square root.
			t = new double[numGroups];
			s = new double[numGroups];
			v = new double[numGroups];
			Fc = new int[numGroups];
			Fw = new int[numGroups];
			int a = 0;
			for (String key : sizes.keySet()) {
				t[a] = sizes.get(key);
				if (positiveSamples != null) {
					Fc[a] = positiveSamples.get(key);
					Fw[a] = negativeSamples.get(key);
				} else {
					Fc[a] = 0;
					Fw[a] = 0;	
				}
				if (selectivities == null) {
					s[a] = (Fc[a] + 1.0) / (Fc[a] + Fw[a] + 2);
				} else {
					s[a] = selectivities.get(key);
				}
				if (variances == null) {
					v[a] = s[a] * (1-s[a]) / (Fc[a] + Fw[a] + 3);
				} else {
					v[a] = variances.get(key);
				}
				a++;
			}
		}
		
		@Override
		public double value(double[] X) {
			Double value = 0.0;
			for (int a = 0; a < numGroups; a++) {
				value += t[a] * t[a] * v[a] * (X[a] - beta) * (X[a] - beta);
				value += 0.25 * t[a];
			}
			value = c_rho * Math.sqrt(value);
			for (int a = 0; a < numGroups; a++) {
				value -= Fc[a] * (1 - beta) + t[a] * X[a] * s[a] - t[a] * beta * s[a];
			}
			return value;
		}

		@Override
		public double[] gradient(double[] X) {
			double[] grad = new double[2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * v[a] * (X[a] - beta) * (X[a] - beta);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a = 0; a < numGroups; a++) {
				grad[a] = c_rho * t[a] * t[a] * v[a] * (X[a] - beta) / var;
				grad[a] -= t[a] * s[a];
			}
			return grad;
		}

		@Override
		public double[][] hessian(double[] X) {
			double[][] hess = new double[2 * numGroups][2 * numGroups];
			double var = 0.0;
			for (int a = 0; a < numGroups; a++) {
				var += t[a] * t[a] * v[a] * (X[a] - beta) * (X[a] - beta);
				var += 0.25 * t[a];
			}
			var = Math.sqrt(var);
			for (int a1 = 0; a1 < numGroups; a1++) {
				hess[a1][a1] = c_rho * t[a1] * t[a1] * v[a1] / var;
				for (int a2 = 0; a2 < numGroups; a2++) {
					hess[a1][a2] -= c_rho * (t[a1] * t[a1] * v[a1] * (X[a1] - beta)) 
							* (t[a2] * t[a2] * v[a2] * (X[a2] - beta)) / (var * var * var);
				}	
			}
			return hess;
		}

		@Override
		public int getDim() {
			return 2 * numGroups;
		}
	}
	
	/**
	 * errorsinSizesEvaluate is our method, that creates ans solves a convex optimization problem, so it satisfies
	 * the precision and recall constraints with probability rho.
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param selectivities		Maps tuple class to expected selectivity of tuples in class.
	 * @param selectivities		Maps tuple class to variance of selectivity of tuples in class.
	 * @param alpha				Precision constraint.
	 * @param beta				Recall constraint.
	 * @param rho				Probability constraint for achieving given precision/recall.
	 * @param retrieveCost		Cost of retrieving a tuple.
	 * @param evaluateCost		Cost of evaluating a tuple.
	 * @param retrieve			Used to return the retrieval probability of tuple class.
	 * @param evaluate			Used to return the evaluation probability of tuple class.
	 * @throws Exception 
	 */
	public static void errorsInSizesEvaluate(Map<String, Double> sizes, Map<String, Double> selectivities,  Map<String, Double> variances, 
			Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples, Double alpha, Double beta, Double rho, 
			Double retrieveCost, Double evaluateCost, Map<String, Double> retrieve, Map<String, Double> evaluate) throws Exception {
		// long startTime = System.currentTimeMillis();
		int numGroups = sizes.size();
		JOptimizer jopt = new JOptimizer();
		OptimizationRequest request = new OptimizationRequest();
		double[] objectiveWeights= new double[2 * numGroups];
		int a = 0;
		Double fixedCost = 0.0;
		for (String key : sizes.keySet()) {
			objectiveWeights[a] = sizes.get(key) * retrieveCost;
			objectiveWeights[a + numGroups] = sizes.get(key) * evaluateCost;
			a++;
			fixedCost += positiveSamples.get(key).doubleValue() + negativeSamples.get(key).doubleValue();
		}
		fixedCost *= (retrieveCost + evaluateCost);
		// Set objective function.
		LinearMultivariateRealFunction objective = new LinearMultivariateRealFunction(objectiveWeights, fixedCost);
		request.setF0(objective);

		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[3 * numGroups + 2];
		
		// Set R_a < 1.0 constraints.
		for (int i = 0; i < numGroups; i++) {
			double[] q = new double[2 * numGroups];
			q[i] = 1.0;
			LinearMultivariateRealFunction rBound = new LinearMultivariateRealFunction(q, -1.0);
			inequalities[i] = rBound;
		}
		// Set E_a > 0.0 constraints.
		for (int i = 0; i < numGroups; i++) {
			double[] q = new double[2 * numGroups];
			q[i + numGroups] = -1.0;
			LinearMultivariateRealFunction eBound = new LinearMultivariateRealFunction(q, 0.0);
			inequalities[i + numGroups] = eBound;
		}
		// Set E_a < R.0 constraints.
		for (int i = 0; i < numGroups; i++) {
			double[] q = new double[2 * numGroups];
			q[i] = -1.0;
			q[i + numGroups] = 1.0;
			LinearMultivariateRealFunction reBound = new LinearMultivariateRealFunction(q, 0.0);
			inequalities[i + 2 * numGroups] = reBound;
		}
		
		// Set Precision Constraint.
		ConvexMultivariateRealFunction precisionConstraintFunction = new PrecisionConstraintFunction(alpha, rho, sizes, selectivities, 
				variances, positiveSamples, negativeSamples);
		inequalities[3 * numGroups] = precisionConstraintFunction;
		
		// Set Recall Constraint.
		ConvexMultivariateRealFunction recallConstraintFunction = new RecallConstraintFunction(beta, rho, sizes, selectivities, 
				variances, positiveSamples, negativeSamples);
		inequalities[3 * numGroups + 1] = recallConstraintFunction;
		request.setFi(inequalities);
		
		double[] initialFeasiblePoint = new double[2 * numGroups];
		for (a = 0; a < numGroups; a++) {
			initialFeasiblePoint[a] = 0.99999;
			initialFeasiblePoint[a + numGroups] = 0.99998;
		}
		request.setInitialPoint(initialFeasiblePoint);
		
		jopt.setOptimizationRequest(request);
		jopt.optimize();
		OptimizationResponse response = jopt.getOptimizationResponse();
		double[] solution = response.getSolution();
		a = 0;
		for (String key : sizes.keySet()) {
			retrieve.put(key, solution[a]);
			evaluate.put(key, solution[a + numGroups]);
			a++;
		}
		// out.println(numGroups + "\t" + (System.currentTimeMillis() - startTime));
	}
	
	public static void main(String[] argv) throws Exception {
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		Map<String, Double> variances= new HashMap<String, Double>();
		Double alpha; 
		Double beta; 
		Double rho; 
		Map<String, Double> retrieve = new HashMap<String, Double>();
		Map<String, Double> evaluate = new HashMap<String, Double>();
		Map<String, Integer> positive = new HashMap<String, Integer>();
		Map<String, Integer> negative = new HashMap<String, Integer>();
		Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
		Double retrieveCost = 1.0;
		Double evaluateCost = 3.0;
		
		// Initialize constraints.
		alpha = 0.8;
		beta = 0.8;
		rho = 0.8;
		
		// Initialize table info.
		sizes.put("pos", 200.0);
		sizes.put("neg", 200.0);
		positive.put("pos", 160);
		positive.put("neg", 40);
		negative.put("pos", 40);
		negative.put("neg", 160);
		selectivities.put("pos", 0.75);
		selectivities.put("neg", 0.25);
		variances.put("pos", 0.0145);
		variances.put("neg", 0.0145);
		positiveSamples.put("pos", 80);
		negativeSamples.put("pos", 20);
		positiveSamples.put("neg", 20);
		negativeSamples.put("neg", 80);
		
		errorsInSizesEvaluate(sizes, null,  null, positiveSamples, negativeSamples, alpha, beta, rho, retrieveCost, evaluateCost, retrieve, evaluate);
		Map<String, Double> stats = PerformanceAnalysis.findStats(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
		Map<String, Double> avgstats = PerformanceAnalysis.precisionRecallThresholdFraction(sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples, alpha, beta, 100);
		System.out.println(stats.toString());
		System.out.println(avgstats.toString());
		//System.out.println(PerformanceAnalysis.retrieveCost(sizes, retrieve));
		//System.out.println(PerformanceAnalysis.evaluateCost(sizes, evaluate));
	}
}
