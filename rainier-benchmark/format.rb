class Benchmark
  def initialize(parts)
    method_parts = parts[1].split(".")
    @klass = method_parts[0].split("Benchmark")[0]
    @method = method_parts[1]
    @params = parts[2..-7].select{|x| x != "N/A"}.join(":")
    @timing = parts[-4].to_f
    @plusminus = parts[-2].to_f
  end

  attr_reader :klass, :method, :params

  def rounded_time
    from = ((@timing - @plusminus) * 1000).to_i
    to = ((@timing + @plusminus) * 1000).to_i
    msb = 2 ** (Math.log(from ^ to) / Math.log(2)).to_i
    (((2**32-1)^(msb-1)) & from | msb).to_s(16)
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
  printf("%-20s %-30s %-30s %20s\n", b.method, b.klass, b.params, b.rounded_time)
end


