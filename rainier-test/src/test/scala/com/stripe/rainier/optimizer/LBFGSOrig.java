/* Extracted and adapted from:
 * RISO: an implementation of distributed belief networks. (Copyright 1999, Robert Dodier).
 *
 * License: Apache license, version 2.
 */
package com.stripe.rainier.optimizer;

class LBFGSOrig
{
	private static double xtol = 1e-16;

	/** Controls the accuracy of the line search <code>mcsrch</code>. If the
	  * function and gradient evaluations are inexpensive with respect
	  * to the cost of the iteration (which is sometimes the case when
	  * solving very large problems) it may be advantageous to set <code>gtol</code>
	  * to a small value. A typical small value is 0.1.  Restriction:
	  * <code>gtol</code> should be greater than 1e-4.
	  */

	public static double gtol = 0.9;

	/** Specify lower bound for the step in the line search.
	  * The default value is 1e-20. This value need not be modified unless
	  * the exponent is too large for the machine being used, or unless
	  * the problem is extremely badly scaled (in which case the exponent
	  * should be increased).
	  */

	public static double stpmin = 1e-20;

	/** Specify upper bound for the step in the line search.
	  * The default value is 1e20. This value need not be modified unless
	  * the exponent is too large for the machine being used, or unless
	  * the problem is extremely badly scaled (in which case the exponent
	  * should be increased).
	  */

	public static double stpmax = 1e20;

	/** The solution vector as it was at the end of the most recently
	  * completed line search. This will usually be different from the
	  * return value of the parameter <tt>x</tt> of <tt>lbfgs</tt>, which
	  * is modified by line-search steps. A caller which wants to stop the
	  * optimization iterations before <tt>LBFGSOrig.lbfgs</tt> automatically stops
	  * (by reaching a very small gradient) should copy this vector instead
	  * of using <tt>x</tt>. When <tt>LBFGSOrig.lbfgs</tt> automatically stops,
	  * then <tt>x</tt> and <tt>solution_cache</tt> are the same.
	  */
	public static double[] solution_cache = null;

	private static double gnorm = 0, stp1 = 0, ftol = 0, stp[] = new double[1], ys = 0, yy = 0, sq = 0, yr = 0, beta = 0, xnorm = 0;
	private static int iter = 0, nfun = 0, point = 0, ispt = 0, iypt = 0, maxfev = 0, info[] = new int[1], bound = 0, npt = 0, cp = 0, i = 0, nfev[] = new int[1], inmc = 0, iycn = 0, iscn = 0;
	private static boolean finish = false;

	private static double[] w = null;
	

	/** This subroutine solves the unconstrained minimization problem
	  * <pre>
	  *     min f(x),    x = (x1,x2,...,x_n),
	  * </pre>
	  * using the limited-memory BFGS method. The routine is especially
	  * effective on problems involving a large number of variables. In
	  * a typical iteration of this method an approximation <code>Hk</code> to the
	  * inverse of the Hessian is obtained by applying <code>m</code> BFGS updates to
	  * a diagonal matrix <code>Hk0</code>, using information from the previous M steps.
	  * The user specifies the number <code>m</code>, which determines the amount of
	  * storage required by the routine. The user may also provide the
	  * diagonal matrices <code>Hk0</code> if not satisfied with the default choice.
	  * The algorithm is described in "On the limited memory BFGS method
	  * for large scale optimization", by D. Liu and J. Nocedal,
	  * Mathematical Programming B 45 (1989) 503-528.
	  *
	  * The user is required to calculate the function value <code>f</code> and its
	  * gradient <code>g</code>. In order to allow the user complete control over
	  * these computations, reverse  communication is used. The routine
	  * must be called repeatedly under the control of the parameter
	  * <code>iflag</code>. 
	  *
	  * The steplength is determined at each iteration by means of the
	  * line search routine <code>mcsrch</code>, which is a slight modification of
	  * the routine <code>CSRCH</code> written by More' and Thuente.
	  *
	  * The only variables that are machine-dependent are <code>xtol</code>,
	  * <code>stpmin</code> and <code>stpmax</code>.
	  *
	  * @param n The number of variables in the minimization problem.
	  *		Restriction: <code>n &gt; 0</code>.
	  *
	  * @param m The number of corrections used in the BFGS update. 
	  *		Values of <code>m</code> less than 3 are not recommended;
	  *		large values of <code>m</code> will result in excessive
	  *		computing time. <code>3 &lt;= m &lt;= 7</code> is recommended.
	  *		Restriction: <code>m &gt; 0</code>.
	  *
	  * @param x On initial entry this must be set by the user to the values
	  *		of the initial estimate of the solution vector. On exit with
	  *		<code>iflag = 0</code>, it contains the values of the variables
	  *		at the best point found (usually a solution).
	  *
	  * @param f Before initial entry and on a re-entry with <code>iflag = 1</code>,
	  *		it must be set by the user to contain the value of the function
	  *		<code>f</code> at the point <code>x</code>.
	  *
	  * @param g Before initial entry and on a re-entry with <code>iflag = 1</code>,
	  *		it must be set by the user to contain the components of the
	  *		gradient <code>g</code> at the point <code>x</code>.
	  *
	  * @param diagco  Set this to <code>true</code> if the user  wishes to
	  *		provide the diagonal matrix <code>Hk0</code> at each iteration.
	  *		Otherwise it should be set to <code>false</code> in which case
	  *		<code>lbfgs</code> will use a default value described below. If
	  *		<code>diagco</code> is set to <code>true</code> the routine will
	  *		return at each iteration of the algorithm with <code>iflag = 2</code>,
	  *		and the diagonal matrix <code>Hk0</code> must be provided in
	  *		the array <code>diag</code>.
	  *
	  * @param diag If <code>diagco = true</code>, then on initial entry or on
	  *		re-entry with <code>iflag = 2</code>, <code>diag</code>
	  *		must be set by the user to contain the values of the 
	  *		diagonal matrix <code>Hk0</code>. Restriction: all elements of
	  *		<code>diag</code> must be positive.
	  *
	  *	@param eps Determines the accuracy with which the solution
	  *		is to be found. The subroutine terminates when
	  *		<pre>
	  *            ||G|| &lt; EPS max(1,||X||),
	  *		</pre>
	  *		where <code>||.||</code> denotes the Euclidean norm.
	  *
	  * @param iflag This must be set to 0 on initial entry to <code>lbfgs</code>.
	  *		A return with <code>iflag &lt; 0</code> indicates an error,
	  *		and <code>iflag = 0</code> indicates that the routine has
	  *		terminated without detecting errors. On a return with
	  *		<code>iflag = 1</code>, the user must evaluate the function
	  *		<code>f</code> and gradient <code>g</code>. On a return with
	  *		<code>iflag = 2</code>, the user must provide the diagonal matrix
	  *		<code>Hk0</code>.
	  *
	  *		The following negative values of <code>iflag</code>, detecting an error,
	  *		are possible:
	  *		<ul>
	  *		<li> <code>iflag = -1</code> The line search routine
	  *			<code>mcsrch</code> failed. One of the following messages
	  *			is printed:
	  *			<ul>
	  *			<li> Improper input parameters.
	  *			<li> Relative width of the interval of uncertainty is at
	  *				most <code>xtol</code>.
	  *			<li> More than 20 function evaluations were required at the
	  *				present iteration.
	  *			<li> The step is too small.
	  *			<li> The step is too large.
	  *			<li> Rounding errors prevent further progress. There may not
	  *				be  a step which satisfies the sufficient decrease and
	  *				curvature conditions. Tolerances may be too small.
	  *			</ul>
	  *		<li><code>iflag = -2</code> The i-th diagonal element of the diagonal inverse
	  *			Hessian approximation, given in DIAG, is not positive.
	  *		<li><code>iflag = -3</code> Improper input parameters for LBFGS
	  *			(<code>n</code> or <code>m</code> are not positive).
	  *		</ul>
	  *
	  */

	public static void lbfgs ( int n , int m , double[] x , double f , double[] g , boolean diagco , double[] diag , double eps , int[] iflag )
	{
		boolean execute_entire_while_loop = false;

		if ( w == null || w.length != n*(2*m+1)+2*m )
		{
			w = new double[ n*(2*m+1)+2*m ];
		}

		if ( iflag[0] == 0 )
		{
			// Initialize.

			solution_cache = new double[n];
			System.arraycopy( x, 0, solution_cache, 0, n );

			iter = 0;

			if ( n <= 0 || m <= 0 )
			{
				iflag[0]= -3;
				return;
			}

			if ( gtol <= 0.0001 )
			{
				gtol= 0.9;
			}

			nfun= 1;
			point= 0;
			finish= false;

			if ( diagco )
			{
				for ( i = 1 ; i <= n ; i += 1 )
				{
					if ( diag [ i -1] <= 0 )
					{
						iflag[0]=-2;
						return;
					}
				}
			}
			else
			{
				for ( i = 1 ; i <= n ; i += 1 )
				{
					diag [ i -1] = 1;
				}
			}
			ispt= n+2*m;
			iypt= ispt+n*m;

			for ( i = 1 ; i <= n ; i += 1 )
			{
				w [ ispt + i -1] = - g [ i -1] * diag [ i -1];
			}

			gnorm = Math.sqrt ( ddot ( n , g , 0, 1 , g , 0, 1 ) );
			stp1= 1/gnorm;
			ftol= 0.0001; 
			maxfev= 20;

			execute_entire_while_loop = true;
		}

		while ( true )
		{
			if ( execute_entire_while_loop )
			{
				iter= iter+1;
				info[0]=0;
				bound=iter-1;
				if ( iter != 1 )
				{
					if ( iter > m ) bound = m;
					ys = ddot ( n , w , iypt + npt , 1 , w , ispt + npt , 1 );
					if ( ! diagco )
					{
						yy = ddot ( n , w , iypt + npt , 1 , w , iypt + npt , 1 );

						for ( i = 1 ; i <= n ; i += 1 )
						{
							diag [ i -1] = ys / yy;
						}
					}
					else
					{
						iflag[0]=2;
						return;
					}
				}
			}

			if ( execute_entire_while_loop || iflag[0] == 2 )
			{
				if ( iter != 1 )
				{
					if ( diagco )
					{
						for ( i = 1 ; i <= n ; i += 1 )
						{
							if ( diag [ i -1] <= 0 )
							{
								iflag[0]=-2;
								return;
							}
						}
					}
					cp= point;
					if ( point == 0 ) cp = m;
					w [ n + cp -1] = 1 / ys;

					for ( i = 1 ; i <= n ; i += 1 )
					{
						w [ i -1] = - g [ i -1];
					}

					cp= point;

					for ( i = 1 ; i <= bound ; i += 1 )
					{
						cp=cp-1;
						if ( cp == - 1 ) cp = m - 1;
						sq = ddot ( n , w , ispt + cp * n , 1 , w , 0 , 1 );
						inmc=n+m+cp+1;
						iycn=iypt+cp*n;
						w [ inmc -1] = w [ n + cp + 1 -1] * sq;
						daxpy ( n , - w [ inmc -1] , w , iycn , 1 , w , 0 , 1 );
					}

					for ( i = 1 ; i <= n ; i += 1 )
					{
						w [ i -1] = diag [ i -1] * w [ i -1];
					}

					for ( i = 1 ; i <= bound ; i += 1 )
					{
						yr = ddot ( n , w , iypt + cp * n , 1 , w , 0 , 1 );
						beta = w [ n + cp + 1 -1] * yr;
						inmc=n+m+cp+1;
						beta = w [ inmc -1] - beta;
						iscn=ispt+cp*n;
						daxpy ( n , beta , w , iscn , 1 , w , 0 , 1 );
						cp=cp+1;
						if ( cp == m ) cp = 0;
					}

					for ( i = 1 ; i <= n ; i += 1 )
					{
						w [ ispt + point * n + i -1] = w [ i -1];
					}
				}

				nfev[0]=0;
				stp[0]=1;
				if ( iter == 1 ) stp[0] = stp1;

				for ( i = 1 ; i <= n ; i += 1 )
				{
					w [ i -1] = g [ i -1];
				}
			}

			Mcsrch.mcsrch ( n , x , f , g , w , ispt + point * n , stp , ftol , xtol , maxfev , info , nfev , diag );

			if ( info[0] == - 1 )
			{
				iflag[0]=1;
				return;
			}

			if ( info[0] != 1 )
			{
				iflag[0]=-1;
				return;
			}

			nfun= nfun + nfev[0];
			npt=point*n;

			for ( i = 1 ; i <= n ; i += 1 )
			{
				w [ ispt + npt + i -1] = stp[0] * w [ ispt + npt + i -1];
				w [ iypt + npt + i -1] = g [ i -1] - w [ i -1];
			}

			point=point+1;
			if ( point == m ) point = 0;

			gnorm = Math.sqrt ( ddot ( n , g , 0 , 1 , g , 0 , 1 ) );
			xnorm = Math.sqrt ( ddot ( n , x , 0 , 1 , x , 0 , 1 ) );
			xnorm = Math.max ( 1.0 , xnorm );

			if ( gnorm / xnorm <= eps ) finish = true;

			// Cache the current solution vector. Due to the spaghetti-like
			// nature of this code, it's not possible to quit here and return;
			// we need to go back to the top of the loop, and eventually call
			// mcsrch one more time -- but that will modify the solution vector.
			// So we need to keep a copy of the solution vector as it was at
			// the completion (info[0]==1) of the most recent line search.

			System.arraycopy( x, 0, solution_cache, 0, n );

			if ( finish )
			{
				iflag[0]=0;
					return;
			}

			execute_entire_while_loop = true;		// from now on, execute whole loop
		}
	}

	/** Compute the sum of a vector times a scalara plus another vector.
	  * Adapted from the subroutine <code>daxpy</code> in <code>lbfgs.f</code>.
	  * There could well be faster ways to carry out this operation; this
	  * code is a straight translation from the Fortran.
	  */ 
	public static void daxpy ( int n , double da , double[] dx , int ix0, int incx , double[] dy , int iy0, int incy )
	{
		int i, ix, iy, m, mp1;

		if ( n <= 0 ) return;

		if ( da == 0 ) return;

		if  ( ! ( incx == 1 && incy == 1 ) )
		{
			ix = 1;
			iy = 1;

			if ( incx < 0 ) ix = ( - n + 1 ) * incx + 1;
			if ( incy < 0 ) iy = ( - n + 1 ) * incy + 1;

			for ( i = 1 ; i <= n ; i += 1 )
			{
				dy [ iy0+iy -1] = dy [ iy0+iy -1] + da * dx [ ix0+ix -1];
				ix = ix + incx;
				iy = iy + incy;
			}

			return;
		}

		m = n % 4;
		if ( m != 0 )
		{
			for ( i = 1 ; i <= m ; i += 1 )
			{
				dy [ iy0+i -1] = dy [ iy0+i -1] + da * dx [ ix0+i -1];
			}

			if ( n < 4 ) return;
		}

		mp1 = m + 1;
		for ( i = mp1 ; i <= n ; i += 4 )
		{
			dy [ iy0+i -1] = dy [ iy0+i -1] + da * dx [ ix0+i -1];
			dy [ iy0+i + 1 -1] = dy [ iy0+i + 1 -1] + da * dx [ ix0+i + 1 -1];
			dy [ iy0+i + 2 -1] = dy [ iy0+i + 2 -1] + da * dx [ ix0+i + 2 -1];
			dy [ iy0+i + 3 -1] = dy [ iy0+i + 3 -1] + da * dx [ ix0+i + 3 -1];
		}
		return;
	}

	/** Compute the dot product of two vectors.
	  * Adapted from the subroutine <code>ddot</code> in <code>lbfgs.f</code>.
	  * There could well be faster ways to carry out this operation; this
	  * code is a straight translation from the Fortran.
	  */ 
	public static double ddot ( int n, double[] dx, int ix0, int incx, double[] dy, int iy0, int incy )
	{
		double dtemp;
		int i, ix, iy, m, mp1;

		dtemp = 0;

		if ( n <= 0 ) return 0;

		if ( !( incx == 1 && incy == 1 ) )
		{
			ix = 1;
			iy = 1;
			if ( incx < 0 ) ix = ( - n + 1 ) * incx + 1;
			if ( incy < 0 ) iy = ( - n + 1 ) * incy + 1;
			for ( i = 1 ; i <= n ; i += 1 )
			{
				dtemp = dtemp + dx [ ix0+ix -1] * dy [ iy0+iy -1];
				ix = ix + incx;
				iy = iy + incy;
			}
			return dtemp;
		}

		m = n % 5;
		if ( m != 0 )
		{
			for ( i = 1 ; i <= m ; i += 1 )
			{
				dtemp = dtemp + dx [ ix0+i -1] * dy [ iy0+i -1];
			}
			if ( n < 5 ) return dtemp;
		}

		mp1 = m + 1;
		for ( i = mp1 ; i <= n ; i += 5 )
		{
			dtemp = dtemp + dx [ ix0+i -1] * dy [ iy0+i -1] + dx [ ix0+i + 1 -1] * dy [ iy0+i + 1 -1] + dx [ ix0+i + 2 -1] * dy [ iy0+i + 2 -1] + dx [ ix0+i + 3 -1] * dy [ iy0+i + 3 -1] + dx [ ix0+i + 4 -1] * dy [ iy0+i + 4 -1];
		}

		return dtemp;
	}
}


/** This class implements an algorithm for multi-dimensional line search.
  * This file is a translation of Fortran code written by Jorge Nocedal.
  * It is distributed as part of the RISO project. See comments in the file
  * <tt>LBFGSOrig.java</tt> for more information.
  */
class Mcsrch
{
	private static int infoc[] = new int[1], j = 0;
	private static double dg = 0, dgm = 0, dginit = 0, dgtest = 0, dgx[] = new double[1], dgxm[] = new double[1], dgy[] = new double[1], dgym[] = new double[1], finit = 0, ftest1 = 0, fm = 0, fx[] = new double[1], fxm[] = new double[1], fy[] = new double[1], fym[] = new double[1], p5 = 0, p66 = 0, stx[] = new double[1], sty[] = new double[1], stmin = 0, stmax = 0, width = 0, width1 = 0, xtrapf = 0;
	private static boolean brackt[] = new boolean[1], stage1 = false;

	static double sqr( double x ) { return x*x; }
	static double max3( double x, double y, double z ) { return x < y ? ( y < z ? z : y ) : ( x < z ? z : x ); }

	/** Minimize a function along a search direction. This code is
	  * a Java translation of the function <code>MCSRCH</code> from
	  * <code>lbfgs.f</code>, which in turn is a slight modification of
	  * the subroutine <code>CSRCH</code> of More' and Thuente.
	  * The changes are to allow reverse communication, and do not affect
	  * the performance of the routine. This function, in turn, calls
	  * <code>mcstep</code>.<p>
	  *
	  * The Java translation was effected mostly mechanically, with some
	  * manual clean-up; in particular, array indices start at 0 instead of 1.
	  * Most of the comments from the Fortran code have been pasted in here
	  * as well.<p>
	  *
	  * The purpose of <code>mcsrch</code> is to find a step which satisfies
	  * a sufficient decrease condition and a curvature condition.<p>
	  *
	  * At each stage this function updates an interval of uncertainty with
	  * endpoints <code>stx</code> and <code>sty</code>. The interval of
	  * uncertainty is initially chosen so that it contains a
	  * minimizer of the modified function
	  * <pre>
	  *      f(x+stp*s) - f(x) - ftol*stp*(gradf(x)'s).
	  * </pre>
	  * If a step is obtained for which the modified function
	  * has a nonpositive function value and nonnegative derivative,
	  * then the interval of uncertainty is chosen so that it
	  * contains a minimizer of <code>f(x+stp*s)</code>.<p>
	  *
	  * The algorithm is designed to find a step which satisfies
	  * the sufficient decrease condition
	  * <pre>
	  *       f(x+stp*s) &lt;= f(X) + ftol*stp*(gradf(x)'s),
	  * </pre>
	  * and the curvature condition
	  * <pre>
	  *       abs(gradf(x+stp*s)'s)) &lt;= gtol*abs(gradf(x)'s).
	  * </pre>
	  * If <code>ftol</code> is less than <code>gtol</code> and if, for example,
	  * the function is bounded below, then there is always a step which
	  * satisfies both conditions. If no step can be found which satisfies both
	  * conditions, then the algorithm usually stops when rounding
	  * errors prevent further progress. In this case <code>stp</code> only
	  * satisfies the sufficient decrease condition.<p>
	  *
	  * @author Original Fortran version by Jorge J. More' and David J. Thuente
	  *	  as part of the Minpack project, June 1983, Argonne National 
	  *   Laboratory. Java translation by Robert Dodier, August 1997.
	  *
	  * @param n The number of variables.
	  *
	  * @param x On entry this contains the base point for the line search.
	  *		On exit it contains <code>x + stp*s</code>.
	  *
	  * @param f On entry this contains the value of the objective function
	  *		at <code>x</code>. On exit it contains the value of the objective
	  *		function at <code>x + stp*s</code>.
	  *
	  * @param g On entry this contains the gradient of the objective function
	  *		at <code>x</code>. On exit it contains the gradient at
	  *		<code>x + stp*s</code>.
	  *
	  *	@param s The search direction.
	  *
	  * @param stp On entry this contains an initial estimate of a satifactory
	  *		step length. On exit <code>stp</code> contains the final estimate.
	  *
	  *	@param ftol Tolerance for the sufficient decrease condition.
	  *
	  * @param xtol Termination occurs when the relative width of the interval
	  *		of uncertainty is at most <code>xtol</code>.
	  *
	  *	@param maxfev Termination occurs when the number of evaluations of
	  *		the objective function is at least <code>maxfev</code> by the end
	  *		of an iteration.
	  *
	  *	@param info This is an output variable, which can have these values:
	  *		<ul>
	  *		<li><code>info = 0</code> Improper input parameters.
	  *		<li><code>info = -1</code> A return is made to compute the function and gradient.
	  *		<li><code>info = 1</code> The sufficient decrease condition and
	  *			the directional derivative condition hold.
	  *		<li><code>info = 2</code> Relative width of the interval of uncertainty
	  *			is at most <code>xtol</code>.
	  *		<li><code>info = 3</code> Number of function evaluations has reached <code>maxfev</code>.
	  *		<li><code>info = 4</code> The step is at the lower bound <code>stpmin</code>.
	  *		<li><code>info = 5</code> The step is at the upper bound <code>stpmax</code>.
	  *		<li><code>info = 6</code> Rounding errors prevent further progress.
	  *			There may not be a step which satisfies the
	  *			sufficient decrease and curvature conditions.
	  *			Tolerances may be too small.
	  *		</ul>
	  *
	  *	@param nfev On exit, this is set to the number of function evaluations.
	  *
	  *	@param wa Temporary storage array, of length <code>n</code>.
	  */

	public static void mcsrch ( int n , double[] x , double f , double[] g , double[] s , int is0 , double[] stp , double ftol , double xtol , int maxfev , int[] info , int[] nfev , double[] wa )
	{
		p5 = 0.5;
		p66 = 0.66;
		xtrapf = 4;

		if ( info[0] != - 1 )
		{
			infoc[0] = 1;
			if ( n <= 0 || stp[0] <= 0 || ftol < 0 || LBFGSOrig.gtol < 0 || xtol < 0 || LBFGSOrig.stpmin < 0 || LBFGSOrig.stpmax < LBFGSOrig.stpmin || maxfev <= 0 ) 
				return;

			// Compute the initial gradient in the search direction
			// and check that s is a descent direction.

			dginit = 0;

			for ( j = 1 ; j <= n ; j += 1 )
			{
				dginit = dginit + g [ j -1] * s [ is0+j -1];
			}


			if ( dginit >= 0 )
			{
				return;
			}

			brackt[0] = false;
			stage1 = true;
			nfev[0] = 0;
			finit = f;
			dgtest = ftol*dginit;
			width = LBFGSOrig.stpmax - LBFGSOrig.stpmin;
			width1 = width/p5;

			for ( j = 1 ; j <= n ; j += 1 )
			{
				wa [ j -1] = x [ j -1];
			}

			// The variables stx, fx, dgx contain the values of the step,
			// function, and directional derivative at the best step.
			// The variables sty, fy, dgy contain the value of the step,
			// function, and derivative at the other endpoint of
			// the interval of uncertainty.
			// The variables stp, f, dg contain the values of the step,
			// function, and derivative at the current step.

			stx[0] = 0;
			fx[0] = finit;
			dgx[0] = dginit;
			sty[0] = 0;
			fy[0] = finit;
			dgy[0] = dginit;
		}

		while ( true )
		{
			if ( info[0] != -1 )
			{
				// Set the minimum and maximum steps to correspond
				// to the present interval of uncertainty.

				if ( brackt[0] )
				{
					stmin = Math.min ( stx[0] , sty[0] );
					stmax = Math.max ( stx[0] , sty[0] );
				}
				else
				{
					stmin = stx[0];
					stmax = stp[0] + xtrapf * ( stp[0] - stx[0] );
				}

				// Force the step to be within the bounds stpmax and stpmin.

				stp[0] = Math.max ( stp[0] , LBFGSOrig.stpmin );
				stp[0] = Math.min ( stp[0] , LBFGSOrig.stpmax );

				// If an unusual termination is to occur then let
				// stp be the lowest point obtained so far.

				if ( ( brackt[0] && ( stp[0] <= stmin || stp[0] >= stmax ) ) || nfev[0] >= maxfev - 1 || infoc[0] == 0 || ( brackt[0] && stmax - stmin <= xtol * stmax ) ) stp[0] = stx[0];

				// Evaluate the function and gradient at stp
				// and compute the directional derivative.
				// We return to main program to obtain F and G.

				for ( j = 1 ; j <= n ; j += 1 )
				{
					x [ j -1] = wa [ j -1] + stp[0] * s [ is0+j -1];
				}

				info[0]=-1;
				return;
			}

			info[0]=0;
			nfev[0] = nfev[0] + 1;
			dg = 0;

			for ( j = 1 ; j <= n ; j += 1 )
			{
				dg = dg + g [ j -1] * s [ is0+j -1];
			}

			ftest1 = finit + stp[0]*dgtest;

			// Test for convergence.

			if ( ( brackt[0] && ( stp[0] <= stmin || stp[0] >= stmax ) ) || infoc[0] == 0 ) info[0] = 6;

			if ( stp[0] == LBFGSOrig.stpmax && f <= ftest1 && dg <= dgtest ) info[0] = 5;

			if ( stp[0] == LBFGSOrig.stpmin && ( f > ftest1 || dg >= dgtest ) ) info[0] = 4;

			if ( nfev[0] >= maxfev ) info[0] = 3;

			if ( brackt[0] && stmax - stmin <= xtol * stmax ) info[0] = 2;

			if ( f <= ftest1 && Math.abs ( dg ) <= LBFGSOrig.gtol * ( - dginit ) ) info[0] = 1;

			// Check for termination.

			if ( info[0] != 0 ) return;

			// In the first stage we seek a step for which the modified
			// function has a nonpositive value and nonnegative derivative.

			if ( stage1 && f <= ftest1 && dg >= Math.min ( ftol , LBFGSOrig.gtol ) * dginit ) stage1 = false;

			// A modified function is used to predict the step only if
			// we have not obtained a step for which the modified
			// function has a nonpositive function value and nonnegative
			// derivative, and if a lower function value has been
			// obtained but the decrease is not sufficient.

			if ( stage1 && f <= fx[0] && f > ftest1 )
			{
				// Define the modified function and derivative values.

				fm = f - stp[0]*dgtest;
				fxm[0] = fx[0] - stx[0]*dgtest;
				fym[0] = fy[0] - sty[0]*dgtest;
				dgm = dg - dgtest;
				dgxm[0] = dgx[0] - dgtest;
				dgym[0] = dgy[0] - dgtest;

				// Call cstep to update the interval of uncertainty
				// and to compute the new step.

				mcstep ( stx , fxm , dgxm , sty , fym , dgym , stp , fm , dgm , brackt , stmin , stmax , infoc );

				// Reset the function and gradient values for f.

				fx[0] = fxm[0] + stx[0]*dgtest;
				fy[0] = fym[0] + sty[0]*dgtest;
				dgx[0] = dgxm[0] + dgtest;
				dgy[0] = dgym[0] + dgtest;
			}
			else
			{
				// Call mcstep to update the interval of uncertainty
				// and to compute the new step.

				mcstep ( stx , fx , dgx , sty , fy , dgy , stp , f , dg , brackt , stmin , stmax , infoc );
			}

			// Force a sufficient decrease in the size of the
			// interval of uncertainty.

			if ( brackt[0] )
			{
				if ( Math.abs ( sty[0] - stx[0] ) >= p66 * width1 ) stp[0] = stx[0] + p5 * ( sty[0] - stx[0] );
				width1 = width;
				width = Math.abs ( sty[0] - stx[0] );
			}
		}
	}

	/** The purpose of this function is to compute a safeguarded step for
	  * a linesearch and to update an interval of uncertainty for
	  * a minimizer of the function.<p>
	  * 
	  * The parameter <code>stx</code> contains the step with the least function
	  * value. The parameter <code>stp</code> contains the current step. It is
	  * assumed that the derivative at <code>stx</code> is negative in the
	  * direction of the step. If <code>brackt[0]</code> is <code>true</code> 
	  * when <code>mcstep</code> returns then a
	  * minimizer has been bracketed in an interval of uncertainty
	  * with endpoints <code>stx</code> and <code>sty</code>.<p>
	  * 
	  * Variables that must be modified by <code>mcstep</code> are 
	  * implemented as 1-element arrays.
	  *
	  * @param stx Step at the best step obtained so far. 
	  *   This variable is modified by <code>mcstep</code>.
	  * @param fx Function value at the best step obtained so far. 
	  *   This variable is modified by <code>mcstep</code>.
	  * @param dx Derivative at the best step obtained so far. The derivative
	  *   must be negative in the direction of the step, that is, <code>dx</code>
	  *   and <code>stp-stx</code> must have opposite signs. 
	  *   This variable is modified by <code>mcstep</code>.
	  * 
	  * @param sty Step at the other endpoint of the interval of uncertainty.
	  *   This variable is modified by <code>mcstep</code>.
	  * @param fy Function value at the other endpoint of the interval of uncertainty.
	  *   This variable is modified by <code>mcstep</code>.
	  * @param dy Derivative at the other endpoint of the interval of
	  *   uncertainty. This variable is modified by <code>mcstep</code>.
	  * 
	  * @param stp Step at the current step. If <code>brackt</code> is set
	  *   then on input <code>stp</code> must be between <code>stx</code>
	  *   and <code>sty</code>. On output <code>stp</code> is set to the
	  *   new step.
	  * @param fp Function value at the current step.
	  * @param dp Derivative at the current step.
	  * 
	  * @param brackt Tells whether a minimizer has been bracketed.
	  *   If the minimizer has not been bracketed, then on input this
	  *   variable must be set <code>false</code>. If the minimizer has
	  *   been bracketed, then on output this variable is <code>true</code>.
	  * 
	  * @param stpmin Lower bound for the step.
	  * @param stpmax Upper bound for the step.
	  * 
	  * @param info On return from <code>mcstep</code>, this is set as follows:
	  *   If <code>info</code> is 1, 2, 3, or 4, then the step has been
	  *   computed successfully. Otherwise <code>info</code> = 0, and this
	  *   indicates improper input parameters.
	  *
	  * @author Jorge J. More, David J. Thuente: original Fortran version,
	  *   as part of Minpack project. Argonne Nat'l Laboratory, June 1983.
	  *   Robert Dodier: Java translation, August 1997.
	  */
	public static void mcstep ( double[] stx , double[] fx , double[] dx , double[] sty , double[] fy , double[] dy , double[] stp , double fp , double dp , boolean[] brackt , double stpmin , double stpmax , int[] info )
	{
		boolean bound;
		double gamma, p, q, r, s, sgnd, stpc, stpf, stpq, theta;

		info[0] = 0;

		if ( ( brackt[0] && ( stp[0] <= Math.min ( stx[0] , sty[0] ) || stp[0] >= Math.max ( stx[0] , sty[0] ) ) ) || dx[0] * ( stp[0] - stx[0] ) >= 0.0 || stpmax < stpmin ) return;

		// Determine if the derivatives have opposite sign.

		sgnd = dp * ( dx[0] / Math.abs ( dx[0] ) );

		if ( fp > fx[0] )
		{
			// First case. A higher function value.
			// The minimum is bracketed. If the cubic step is closer
			// to stx than the quadratic step, the cubic step is taken,
			// else the average of the cubic and quadratic steps is taken.

			info[0] = 1;
			bound = true;
			theta = 3 * ( fx[0] - fp ) / ( stp[0] - stx[0] ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) );
			if ( stp[0] < stx[0] ) gamma = - gamma;
			p = ( gamma - dx[0] ) + theta;
			q = ( ( gamma - dx[0] ) + gamma ) + dp;
			r = p/q;
			stpc = stx[0] + r * ( stp[0] - stx[0] );
			stpq = stx[0] + ( ( dx[0] / ( ( fx[0] - fp ) / ( stp[0] - stx[0] ) + dx[0] ) ) / 2 ) * ( stp[0] - stx[0] );
			if ( Math.abs ( stpc - stx[0] ) < Math.abs ( stpq - stx[0] ) )
			{
				stpf = stpc;
			}
			else
			{
				stpf = stpc + ( stpq - stpc ) / 2;
			}
			brackt[0] = true;
		}
		else if ( sgnd < 0.0 )
		{
			// Second case. A lower function value and derivatives of
			// opposite sign. The minimum is bracketed. If the cubic
			// step is closer to stx than the quadratic (secant) step,
			// the cubic step is taken, else the quadratic step is taken.

			info[0] = 2;
			bound = false;
			theta = 3 * ( fx[0] - fp ) / ( stp[0] - stx[0] ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) );
			if ( stp[0] > stx[0] ) gamma = - gamma;
			p = ( gamma - dp ) + theta;
			q = ( ( gamma - dp ) + gamma ) + dx[0];
			r = p/q;
			stpc = stp[0] + r * ( stx[0] - stp[0] );
			stpq = stp[0] + ( dp / ( dp - dx[0] ) ) * ( stx[0] - stp[0] );
			if ( Math.abs ( stpc - stp[0] ) > Math.abs ( stpq - stp[0] ) )
			{
				stpf = stpc;
			}
			else
			{
				stpf = stpq;
			}
			brackt[0] = true;
		}
		else if ( Math.abs ( dp ) < Math.abs ( dx[0] ) )
		{
			// Third case. A lower function value, derivatives of the
			// same sign, and the magnitude of the derivative decreases.
			// The cubic step is only used if the cubic tends to infinity
			// in the direction of the step or if the minimum of the cubic
			// is beyond stp. Otherwise the cubic step is defined to be
			// either stpmin or stpmax. The quadratic (secant) step is also
			// computed and if the minimum is bracketed then the the step
			// closest to stx is taken, else the step farthest away is taken.

			info[0] = 3;
			bound = true;
			theta = 3 * ( fx[0] - fp ) / ( stp[0] - stx[0] ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( Math.max ( 0, sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) ) );
			if ( stp[0] > stx[0] ) gamma = - gamma;
			p = ( gamma - dp ) + theta;
			q = ( gamma + ( dx[0] - dp ) ) + gamma;
			r = p/q;
			if ( r < 0.0 && gamma != 0.0 )
			{
				stpc = stp[0] + r * ( stx[0] - stp[0] );
			}
			else if ( stp[0] > stx[0] )
			{
				stpc = stpmax;
			}
			else
			{
				stpc = stpmin;
			}
			stpq = stp[0] + ( dp / ( dp - dx[0] ) ) * ( stx[0] - stp[0] );
			if ( brackt[0] )
			{
				if ( Math.abs ( stp[0] - stpc ) < Math.abs ( stp[0] - stpq ) )
				{
					stpf = stpc;
				}
				else
				{
					stpf = stpq;
				}
			}
			else
			{
				if ( Math.abs ( stp[0] - stpc ) > Math.abs ( stp[0] - stpq ) )
				{
					stpf = stpc;
				}
				else
				{
					stpf = stpq;
				}
			}
		}
		else
		{
			// Fourth case. A lower function value, derivatives of the
			// same sign, and the magnitude of the derivative does
			// not decrease. If the minimum is not bracketed, the step
			// is either stpmin or stpmax, else the cubic step is taken.

			info[0] = 4;
			bound = false;
			if ( brackt[0] )
			{
				theta = 3 * ( fp - fy[0] ) / ( sty[0] - stp[0] ) + dy[0] + dp;
				s = max3 ( Math.abs ( theta ) , Math.abs ( dy[0] ) , Math.abs ( dp ) );
				gamma = s * Math.sqrt ( sqr( theta / s ) - ( dy[0] / s ) * ( dp / s ) );
				if ( stp[0] > sty[0] ) gamma = - gamma;
				p = ( gamma - dp ) + theta;
				q = ( ( gamma - dp ) + gamma ) + dy[0];
				r = p/q;
				stpc = stp[0] + r * ( sty[0] - stp[0] );
				stpf = stpc;
			}
			else if ( stp[0] > stx[0] )
			{
				stpf = stpmax;
			}
			else
			{
				stpf = stpmin;
			}
		}

		// Update the interval of uncertainty. This update does not
		// depend on the new step or the case analysis above.

		if ( fp > fx[0] )
		{
			sty[0] = stp[0];
			fy[0] = fp;
			dy[0] = dp;
		}
		else
		{
			if ( sgnd < 0.0 )
			{
				sty[0] = stx[0];
				fy[0] = fx[0];
				dy[0] = dx[0];
			}
			stx[0] = stp[0];
			fx[0] = fp;
			dx[0] = dp;
		}

		// Compute the new step and safeguard it.

		stpf = Math.min ( stpmax , stpf );
		stpf = Math.max ( stpmin , stpf );
		stp[0] = stpf;

		if ( brackt[0] && bound )
		{
			if ( sty[0] > stx[0] )
			{
				stp[0] = Math.min ( stx[0] + 0.66 * ( sty[0] - stx[0] ) , stp[0] );
			}
			else
			{
				stp[0] = Math.max ( stx[0] + 0.66 * ( sty[0] - stx[0] ) , stp[0] );
			}
		}

		return;
	}
}