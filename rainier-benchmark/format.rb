class Benchmark
  def initialize(parts)
    method_parts = parts[1].split(".")
    if method_parts.size > 5
      method_parts = method_parts[5..-1]
      @klass = method_parts[0].split("Benchmark")[0]
      @method = method_parts[1].split(":")[0]
      @pctile = method_parts[2].to_i
      @params = parts[2..-4].select{|x| x != "N/A"}.join(":")
      @timing = parts[-2].to_f
    end
  end

  attr_reader :klass, :method, :params, :pctile, :timing

  def rounded_time
    diff = @timing / 5
    oom = 10 ** Math.log10(diff).ceil
    (@timing / oom).round.to_f * oom
  end
end

p50 = []
ARGF.each do |line|
  stripped = line.gsub(/\e\[.*?m/, "")

  if stripped =~ /Run progress/
    $stderr.puts line
  end

  parts = stripped.split
  if(parts[-1] == "us/op" && parts[1] =~ /p0[.]/)
    b = Benchmark.new(parts)
    if(b.pctile == 50)
        p50 << b
    end
  end
end

p50.sort_by!{|b| [b.method, b.klass, b.params]}
p50.each do |b|
  printf("%-20s %-25s %-20s %15.3f\n", b.method, b.klass, b.params, b.rounded_time)
end