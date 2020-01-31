/* Heavily modified from:
 * RISO: an implementation of distributed belief networks. (Copyright 1999, Robert Dodier).
 *
 * License: Apache license, version 2.
 */
package com.stripe.rainier.optimizer;

class LBFGS
{
	private double stp1;
	private double ys;
	private double yy;
	private double yr;
	private int iter;
	private int point;
	private int ispt;
	private int iypt;
	private int bound;
	private int npt;

	private int inmc;

	private int info;
	private double stp;
	private int nfev;
	private double[] w;
	private int m;
	private int n;
	private double eps;
	private double[] diag;
	private double[] x;


	/*  m The number of corrections used in the BFGS update. 
	*		Values of less than 3 are not recommended;
	*		large values will result in excessive
	*		computing time. 3-7 is recommended.
	*
	*	 eps Determines the accuracy with which the solution
	*		is to be found. The subroutine terminates when
	*       ||G|| < eps * max(1,||X||)
	*/

	public LBFGS(double[] x, int m, double eps) {
		this.x = x;
		this.m = m;
		this.n = x.length;
		this.eps = eps;

		w = new double[ n*(2*m+1)+2*m ];

		iter = 0;
		point= 0;

		diag = new double[n];
		for (int i = 0 ; i < n ; i += 1 )
			diag [i] = 1;

		ispt= n+2*m;
		iypt= ispt+n*m;
	}

	public boolean apply (double f, double[] g)
	{
		boolean execute_entire_while_loop = false;
		if ( iter == 0 )
		{
			//initialize
			for (int i = 0 ; i < n ; i += 1 )
			{
				w[ispt + i] = -g[i] * diag[i];
			}
	
			double gnorm = Math.sqrt ( ddot ( n , g , 0, 1 , g , 0, 1 ) );
			stp1= 1/gnorm;	
			execute_entire_while_loop = true;
		}

		while ( true )
		{
			if ( execute_entire_while_loop )
			{
				iter= iter+1;
				info=0;
				bound=iter-1;
				if ( iter != 1 )
				{
					if ( iter > m ) bound = m;
					ys = ddot ( n , w , iypt + npt , 1 , w , ispt + npt , 1 );
					yy = ddot ( n , w , iypt + npt , 1 , w , iypt + npt , 1 );

					for ( int i = 0 ; i < n ; i += 1 )
						diag [i] = ys / yy;
				}
			}

			if ( execute_entire_while_loop)
			{
				if ( iter != 1 )
				{
					int cp= point;
					if ( point == 0 ) cp = m;
					w [ n + cp -1] = 1 / ys;

					for ( int i = 0 ; i < n ; i += 1 )
					{
						w[i] = -g[i];
					}

					cp= point;

					for ( int i = 0 ; i < bound ; i += 1 )
					{
						cp=cp-1;
						if ( cp == - 1 ) cp = m - 1;
						double sq = ddot ( n , w , ispt + cp * n , 1 , w , 0 , 1 );
						inmc=n+m+cp;
						int iycn=iypt+cp*n;
						w [inmc] = w [n + cp] * sq;
						daxpy ( n , -w[inmc] , w , iycn , 1 , w , 0 , 1 );
					}

					for ( int i = 0 ; i < n ; i += 1 )
					{
						w [i] = diag [i] * w[i];
					}

					for ( int i = 0 ; i < bound ; i += 1 )
					{
						yr = ddot ( n , w , iypt + cp * n , 1 , w , 0 , 1 );
						double beta = w [ n + cp] * yr;
						inmc=n+m+cp;
						beta = w [inmc] - beta;
						int iscn=ispt+cp*n;
						daxpy ( n , beta , w , iscn , 1 , w , 0 , 1 );
						cp=cp+1;
						if ( cp == m ) cp = 0;
					}

					for ( int i = 0 ; i < n ; i += 1 )
					{
						w[ispt + point * n + i] = w[i];
					}
				}

				nfev=0;
				stp=1;
				if ( iter == 1 ) stp = stp1;

				for (int i = 0; i < n; i += 1 )
				{
					w[i] = g[i];
				}
			}

			mcsrch(f , g);

			if ( info == -1 )
				return false;

			npt=point*n;

			for ( int i = 0 ; i < n ; i += 1 )
			{
				w [ ispt + npt + i] = stp * w [ ispt + npt + i];
				w [ iypt + npt + i] = g [i] - w[i];
			}

			point=point+1;
			if ( point == m ) point = 0;

			double gnorm = Math.sqrt ( ddot ( n , g , 0 , 1 , g , 0 , 1 ) );
			double xnorm = Math.sqrt ( ddot ( n , x , 0 , 1 , x , 0 , 1 ) );
			xnorm = Math.max ( 1.0 , xnorm );

			if ( gnorm / xnorm <= eps )
				return true;

			execute_entire_while_loop = true;		// from now on, execute whole loop
		}
	}


	private static double gtol = 0.9;

	private static double STPMIN = 1e-20;
	private static double STPMAX = 1e20;

	private static double xtol = 1e-16;
	private static double ftol= 0.0001; 
	private static int maxfev= 20;

	private static double p5 = 0.5;
	private static double p66 = 0.66;

	private static double xtrapf = 4;


	private double dg;
	private double dgm;
	private double dginit;
	private double dgtest;
	private double finit;
	private double ftest1;
	private double fm;
	private double stmin;
	private double stmax;
	private double width;
	private double width1;

	private boolean stage1 = false;

	private int infoc;
	private boolean brackt;

	private double dgx[] = new double[1];
	private double dgy[] = new double[1];
	private double fx[] = new double[1];
	private double fy[] = new double[1];

	private double dgxm[] = new double[1];
	private double dgym[] = new double[1];
	private double fxm[] = new double[1];
	private double fym[] = new double[1];

	private double stx;
	private double sty;

	private void mcsrch (double f , double[] g)
	{
		int is0 = ispt + point * n;
		if ( info != - 1 )
		{
			infoc = 1;

			// Compute the initial gradient in the search direction
			// and check that s is a descent direction.

			dginit = 0;

			for ( int j = 0 ; j < n ; j += 1 )
				dginit = dginit + g [j] * w[is0+j];

			if ( dginit >= 0 )
				throw new RuntimeException("dginit");

			brackt = false;
			stage1 = true;
			nfev = 0;
			finit = f;
			dgtest = ftol*dginit;
			width = STPMAX - STPMIN;
			width1 = width/p5;

			for ( int j = 0 ; j < n ; j += 1 )
				diag[j] = x[j];

			// The variables stx, fx, dgx contain the values of the step,
			// function, and directional derivative at the best step.
			// The variables sty, fy, dgy contain the value of the step,
			// function, and derivative at the other endpoint of
			// the interval of uncertainty.
			// The variables stp, f, dg contain the values of the step,
			// function, and derivative at the current step.

			stx = 0;
			fx[0] = finit;
			dgx[0] = dginit;
			sty = 0;
			fy[0] = finit;
			dgy[0] = dginit;
		}

		while ( true )
		{
			if ( info != -1 )
			{
				// Set the minimum and maximum steps to correspond
				// to the present interval of uncertainty.

				if ( brackt )
				{
					stmin = Math.min ( stx , sty );
					stmax = Math.max ( stx , sty );
				}
				else
				{
					stmin = stx;
					stmax = stp + xtrapf * ( stp - stx );
				}

				// Force the step to be within the bounds stmax and stmin.

				stp = Math.max ( stp , STPMIN );
				stp = Math.min ( stp , STPMAX );

				// If an unusual termination is to occur then let
				// stp be the lowest point obtained so far.

				if ( ( brackt && ( stp <= stmin || stp >= stmax ) ) || nfev >= maxfev - 1 || infoc == 0 || ( brackt && stmax - stmin <= xtol * stmax ) ) stp = stx;

				// Evaluate the function and gradient at stp
				// and compute the directional derivative.
				// We return to main program to obtain F and G.

				for (int j = 0; j < n ; j += 1 )
					x [j] = diag[j] + stp * w[ is0+j];

				info=-1;
				return;
			}

			info=0;
			nfev = nfev + 1;
			dg = 0;

			for (int j = 0 ; j < n ; j += 1 )
			{
				dg = dg + g [ j] * w [ is0+j];
			}

			ftest1 = finit + stp*dgtest;

			// Test for convergence.

			if ( ( brackt && ( stp <= stmin || stp >= stmax ) ) || infoc == 0 ) info = 6;

			if ( stp == STPMAX && f <= ftest1 && dg <= dgtest ) info = 5;

			if ( stp == STPMIN && ( f > ftest1 || dg >= dgtest ) ) info = 4;

			if ( nfev >= maxfev ) info = 3;

			if ( brackt && stmax - stmin <= xtol * stmax ) info = 2;

			if ( f <= ftest1 && Math.abs ( dg ) <= gtol * ( - dginit ) ) info = 1;

			// Check for termination.

			if ( info != 0 ) return;

			// In the first stage we seek a step for which the modified
			// function has a nonpositive value and nonnegative derivative.

			if ( stage1 && f <= ftest1 && dg >= Math.min ( ftol , gtol ) * dginit ) stage1 = false;

			// A modified function is used to predict the step only if
			// we have not obtained a step for which the modified
			// function has a nonpositive function value and nonnegative
			// derivative, and if a lower function value has been
			// obtained but the decrease is not sufficient.

			if ( stage1 && f <= fx[0] && f > ftest1 )
			{
				// Define the modified function and derivative values.

				fm = f - stp*dgtest;
				fxm[0] = fx[0] - stx*dgtest;
				fym[0] = fy[0] - sty*dgtest;
				dgm = dg - dgtest;
				dgxm[0] = dgx[0] - dgtest;
				dgym[0] = dgy[0] - dgtest;

				// Call cstep to update the interval of uncertainty
				// and to compute the new step.

				mcstep (fxm , dgxm , fym , dgym , fm , dgm);

				// Reset the function and gradient values for f.

				fx[0] = fxm[0] + stx*dgtest;
				fy[0] = fym[0] + sty*dgtest;
				dgx[0] = dgxm[0] + dgtest;
				dgy[0] = dgym[0] + dgtest;
			}
			else
			{
				// Call mcstep to update the interval of uncertainty
				// and to compute the new step.

				mcstep (fx , dgx , fy , dgy , f , dg);
			}

			// Force a sufficient decrease in the size of the
			// interval of uncertainty.

			if ( brackt )
			{
				if ( Math.abs ( sty - stx ) >= p66 * width1 )
					stp = stx + p5 * ( sty - stx );
				width1 = width;
				width = Math.abs ( sty - stx );
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
	  * direction of the step. If <code>brackt</code> is <code>true</code> 
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
	  * @param stmin Lower bound for the step.
	  * @param stmax Upper bound for the step.
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
	  private void mcstep (double[] fx , double[] dx , double[] fy , double[] dy , double fp , double dp)
	  {
		boolean bound;
		double gamma, p, q, r, s, sgnd, stpc, stpf, stpq, theta;

		infoc = 0;

		if ( ( brackt && ( stp <= Math.min ( stx , sty ) || stp >= Math.max ( stx , sty ) ) ) || dx[0] * ( stp - stx ) >= 0.0 || stmax < stmin ) return;

		// Determine if the derivatives have opposite sign.

		sgnd = dp * ( dx[0] / Math.abs ( dx[0] ) );

		if ( fp > fx[0] )
		{
			// First case. A higher function value.
			// The minimum is bracketed. If the cubic step is closer
			// to stx than the quadratic step, the cubic step is taken,
			// else the average of the cubic and quadratic steps is taken.

			infoc = 1;
			bound = true;
			theta = 3 * ( fx[0] - fp ) / ( stp - stx ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) );
			if ( stp < stx ) gamma = - gamma;
			p = ( gamma - dx[0] ) + theta;
			q = ( ( gamma - dx[0] ) + gamma ) + dp;
			r = p/q;
			stpc = stx + r * ( stp - stx );
			stpq = stx + ( ( dx[0] / ( ( fx[0] - fp ) / ( stp - stx ) + dx[0] ) ) / 2 ) * ( stp - stx );
			if ( Math.abs ( stpc - stx ) < Math.abs ( stpq - stx ) )
			{
				stpf = stpc;
			}
			else
			{
				stpf = stpc + ( stpq - stpc ) / 2;
			}
			brackt = true;
		}
		else if ( sgnd < 0.0 )
		{
			// Second case. A lower function value and derivatives of
			// opposite sign. The minimum is bracketed. If the cubic
			// step is closer to stx than the quadratic (secant) step,
			// the cubic step is taken, else the quadratic step is taken.

			infoc = 2;
			bound = false;
			theta = 3 * ( fx[0] - fp ) / ( stp - stx ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) );
			if ( stp > stx ) gamma = - gamma;
			p = ( gamma - dp ) + theta;
			q = ( ( gamma - dp ) + gamma ) + dx[0];
			r = p/q;
			stpc = stp + r * ( stx - stp );
			stpq = stp + ( dp / ( dp - dx[0] ) ) * ( stx - stp );
			if ( Math.abs ( stpc - stp ) > Math.abs ( stpq - stp ) )
			{
				stpf = stpc;
			}
			else
			{
				stpf = stpq;
			}
			brackt = true;
		}
		else if ( Math.abs ( dp ) < Math.abs ( dx[0] ) )
		{
			// Third case. A lower function value, derivatives of the
			// same sign, and the magnitude of the derivative decreases.
			// The cubic step is only used if the cubic tends to infinity
			// in the direction of the step or if the minimum of the cubic
			// is beyond stp. Otherwise the cubic step is defined to be
			// either stmin or stmax. The quadratic (secant) step is also
			// computed and if the minimum is bracketed then the the step
			// closest to stx is taken, else the step farthest away is taken.

			infoc = 3;
			bound = true;
			theta = 3 * ( fx[0] - fp ) / ( stp - stx ) + dx[0] + dp;
			s = max3 ( Math.abs ( theta ) , Math.abs ( dx[0] ) , Math.abs ( dp ) );
			gamma = s * Math.sqrt ( Math.max ( 0, sqr( theta / s ) - ( dx[0] / s ) * ( dp / s ) ) );
			if ( stp > stx ) gamma = - gamma;
			p = ( gamma - dp ) + theta;
			q = ( gamma + ( dx[0] - dp ) ) + gamma;
			r = p/q;
			if ( r < 0.0 && gamma != 0.0 )
			{
				stpc = stp + r * ( stx - stp );
			}
			else if ( stp > stx )
			{
				stpc = stmax;
			}
			else
			{
				stpc = stmin;
			}
			stpq = stp + ( dp / ( dp - dx[0] ) ) * ( stx - stp );
			if ( brackt )
			{
				if ( Math.abs ( stp - stpc ) < Math.abs ( stp - stpq ) )
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
				if ( Math.abs ( stp - stpc ) > Math.abs ( stp - stpq ) )
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
			// is either stmin or stmax, else the cubic step is taken.

			infoc = 4;
			bound = false;
			if ( brackt )
			{
				theta = 3 * ( fp - fy[0] ) / ( sty - stp ) + dy[0] + dp;
				s = max3 ( Math.abs ( theta ) , Math.abs ( dy[0] ) , Math.abs ( dp ) );
				gamma = s * Math.sqrt ( sqr( theta / s ) - ( dy[0] / s ) * ( dp / s ) );
				if ( stp > sty ) gamma = - gamma;
				p = ( gamma - dp ) + theta;
				q = ( ( gamma - dp ) + gamma ) + dy[0];
				r = p/q;
				stpc = stp + r * ( sty - stp );
				stpf = stpc;
			}
			else if ( stp > stx )
			{
				stpf = stmax;
			}
			else
			{
				stpf = stmin;
			}
		}

		// Update the interval of uncertainty. This update does not
		// depend on the new step or the case analysis above.

		if ( fp > fx[0] )
		{
			sty = stp;
			fy[0] = fp;
			dy[0] = dp;
		}
		else
		{
			if ( sgnd < 0.0 )
			{
				sty = stx;
				fy[0] = fx[0];
				dy[0] = dx[0];
			}
			stx = stp;
			fx[0] = fp;
			dx[0] = dp;
		}

		// Compute the new step and safeguard it.

		stpf = Math.min ( stmax , stpf );
		stpf = Math.max ( stmin , stpf );
		stp = stpf;

		if ( brackt && bound )
		{
			if ( sty > stx )
			{
				stp = Math.min ( stx + 0.66 * ( sty - stx ) , stp );
			}
			else
			{
				stp = Math.max ( stx + 0.66 * ( sty - stx ) , stp );
			}
		}

		return;
	}

	/** Compute the sum of a vector times a scalar plus another vector.
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

	static double sqr( double x ) { return x*x; }
	static double max3( double x, double y, double z ) { return x < y ? ( y < z ? z : y ) : ( x < z ? z : x ); }
}

