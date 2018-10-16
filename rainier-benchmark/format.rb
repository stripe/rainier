class Benchmark
  def initialize(parts)
    method_parts = parts[1].split(".")
    @klass = method_parts[0].split("Benchmark")[0]
    @method = method_parts[1]
    @params = parts[2..-7].join(":")
    @timing = parts[-4].to_f
    @stddev = parts[-2].to_f
  end

  attr_reader :klass, :method, :params

  def rounded_time
    oom = 10 ** Math.log10(@stddev).to_i
    rnd_dev = ((@stddev / oom).ceil * oom) * 2
    (@timing.to_f / rnd_dev).ceil.to_f * rnd_dev
  end
end

benchmarks = []
ARGF.each do |line|
  stripped = line.gsub(/\e\[.*?m/, "")

  if stripped =~ /Run progress/
    $stderr.puts line
  end

  parts = stripped.split
  if(parts[-6] == "avgt")
    benchmarks << Benchmark.new(parts)
  end
end

benchmarks.sort_by!{|b| [b.method, b.klass, b.params]}
benchmarks.each do |b|
  printf("%-20s %-20s %-20s %20.3f\n", b.method, b.klass, b.params, b.rounded_time)
end