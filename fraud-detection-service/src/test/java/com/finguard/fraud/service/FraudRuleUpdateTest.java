package com.finguard.fraud.service;

import com.finguard.fraud.decision.DecisionConfig;
import com.finguard.fraud.decision.DecisionMaker;
import com.finguard.fraud.dto.FraudRuleUpdateRequest;
import com.finguard.fraud.entity.FraudRule;
import com.finguard.fraud.event.FraudRuleUpdated;
import com.finguard.fraud.exception.StaleVersionException;
import com.finguard.fraud.repository.FraudAnalysisRepository;
import com.finguard.fraud.repository.FraudRuleRepository;
import com.finguard.fraud.rule.TestFraudProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRuleUpdateTest {

    @Mock RuleEngineService ruleEngineService;
    @Mock FraudAnalysisRepository analysisRepository;
    @Mock FraudRuleRepository ruleRepository;
    @Mock ApplicationEventPublisher events;

    private final UUID ruleId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private FraudAnalysisService service() {
        var properties = TestFraudProperties.defaults();
        var decisionMaker = new DecisionMaker(properties, new DecisionConfig().ruleFailurePolicy(properties));
        return new FraudAnalysisService(ruleEngineService, decisionMaker, analysisRepository, ruleRepository, events,
                new com.finguard.fraud.ml.MlRule(mock(com.finguard.fraud.ml.MlScorer.class),
                        mock(RuleConfigService.class), new com.finguard.fraud.ml.MlProperties(true, "classpath:ml/", 0.8)),
                mock(com.finguard.fraud.repository.FraudLabelRepository.class));
    }

    private FraudRule rule(long version) {
        return FraudRule.builder().id(ruleId).code("VELOCITY").name("Velocity Check")
                .enabled(true).weight(25).version(version).build();
    }

    @Test
    void updateBasedOnCurrentVersion_isApplied_andAudited() {
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule(3)));
        when(ruleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FraudRule updated = service().updateRule(ruleId, 3, new FraudRuleUpdateRequest(null, 40), adminId, "corr-12345678");

        assertThat(updated.getWeight()).isEqualTo(40);
        assertThat(updated.isEnabled()).isTrue();
        verify(events).publishEvent(argThat((Object e) -> e instanceof FraudRuleUpdated u
                && u.oldWeight() == 25 && u.oldEnabled() && u.actorId().equals(adminId)));
    }

    @Test
    void updateBasedOnStaleVersion_isRejected_andNothingIsWritten() {
        // Another admin already saved version 4; this one edited version 3.
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule(4)));

        assertThatThrownBy(() -> service().updateRule(ruleId, 3, new FraudRuleUpdateRequest(false, null), adminId, null))
                .isInstanceOf(StaleVersionException.class)
                .satisfies(e -> assertThat(((StaleVersionException) e).getCurrentVersion()).isEqualTo(4));

        verify(ruleRepository, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }
}
