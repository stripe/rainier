#!/usr/bin/env ruby

require 'json'

PROBLEM_MATCHER = {
  "owner" => "scala",
  "fileLocation" => ["relative", "${workspaceRoot}"],
  "pattern" => {
      "regexp" => "^(.*):(\\d+):\\s+(warning|error):\\s+(.*)$",
      "file" => 1,
      "line" => 2,
      "severity" => 3,
      "message" => 4
  }
}

def bazel_command(kind)
  case kind
  when /test/
    "test"
  when /binary/
    "run"
  else
    "build"
  end
end

def bazel_task(target, kind)
  command = bazel_command(kind)
  task =
    {
        "taskName" => "#{command} #{target}",
        "args" => [command, target]
    }

  if command == "build"
    task["problemMatcher"] = PROBLEM_MATCHER
  end
  task
end

targets = `bazel query '...' --output label_kind`.split("\n").map do |line|
  kind, rule, target = line.chomp.split(" ")
  [target, kind]
end

targets.select!{|target, kind| target !~ /3rdparty/}

tasks = {
  "version" => "0.1.0",
  "command" => "bazel",
  "isShellCommand" => true,
  "args" => [],
  "showOutput" => "silent",
  "suppressTaskName" => true,
  "tasks" => (targets.map do |target, kind|
    bazel_task(target, kind)
  end) + [{
    "taskName" => "build ...",
    "args" => ["build", "..."],
    "isBuildCommand" => true,
    "problemMatcher" => PROBLEM_MATCHER
  }]
}

puts JSON.pretty_generate(tasks)