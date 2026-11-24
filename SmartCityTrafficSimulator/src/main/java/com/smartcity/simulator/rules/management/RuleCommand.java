package com.smartcity.simulator.rules.management;

import com.smartcity.simulator.rules.builder.CompiledRule;
import com.smartcity.simulator.rules.builder.RuleDraft;
import java.util.Objects;

/** Immutable requests. The engine—not Swing—applies these at a safe boundary. */
public sealed interface RuleCommand permits
        RuleCommand.Add, RuleCommand.Replace,
        RuleCommand.SetEnabled, RuleCommand.Delete {

    String id();

    record Add(String id, RuleDraft draft, CompiledRule compiled)
            implements RuleCommand {
        public Add {
            Objects.requireNonNull(id);
            Objects.requireNonNull(draft);
            Objects.requireNonNull(compiled);
        }
    }

    record Replace(String id, RuleDraft draft, CompiledRule compiled)
            implements RuleCommand {
        public Replace {
            Objects.requireNonNull(id);
            Objects.requireNonNull(draft);
            Objects.requireNonNull(compiled);
        }
    }

    record SetEnabled(String id, boolean enabled) implements RuleCommand {
        public SetEnabled { Objects.requireNonNull(id); }
    }

    record Delete(String id) implements RuleCommand {
        public Delete { Objects.requireNonNull(id); }
    }
}
