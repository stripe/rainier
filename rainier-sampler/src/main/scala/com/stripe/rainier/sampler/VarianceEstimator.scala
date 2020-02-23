package com.stripe.rainier.sampler

/*
    def add_sample(self, x, weight):
        x = np.asarray(x)
        self.w_sum += weight
        self.w_sum2 += weight * weight
        prop = weight / self.w_sum
        old_diff = x - self.mean
        self.mean[:] += prop * old_diff
        new_diff = x - self.mean
        self.raw_var[:] += weight * old_diff * new_diff

    def current_variance(self, out=None):
        if self.w_sum == 0:
            raise ValueError("Can not compute variance without samples.")
        if out is not None:
            return np.divide(self.raw_var, self.w_sum, out=out)
        else:
            return (self.raw_var / self.w_sum).astype(self._dtype)

    def current_mean(self):
        return self.mean.copy(dtype=self._dtype)
 */

class VarianceEstimator(size: Int) {
  var samples = 0
  val mean = new Array[Double](size)
  val variance = new Array[Double](size)

  val oldDiff = new Array[Double](size)
  val newDiff = new Array[Double](size)

  private def diff(sample: Array[Double], buf: Array[Double]): Unit = {
    var i = 0
    while (i < size) {
      buf(i) = sample(i) - mean(i)
      i += 1
    }
  }

  def update(sample: Array[Double]): Unit = {
    samples += 1
    diff(sample, oldDiff)
    var i = 0
    while (i < size) {
      mean(i) += (oldDiff(i) / samples.toDouble)
      i += 1
    }
    diff(sample, newDiff)

    var j = 0
    while (j < size) {
      variance(j) += oldDiff(j) * newDiff(j)
      j += 1
    }
  }

  def close(): Array[Double] = {
    var i = 0
    while (i < size) {
      variance(i) /= samples.toDouble
      i += 1
    }
    variance
  }
}
