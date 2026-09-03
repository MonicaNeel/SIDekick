package sidekick.answering.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sidekick.answering.Answer;
import sidekick.answering.AnswerService;

/**
 * Ask one question from the terminal — the whole pipeline end to end:
 *   --sidekick.ask.question="what is the exit load?" --sidekick.ask.fund=hdfc-flexi-cap
 * Prints the answer (or refusal) plus a terminal-sized version of the debug
 * panel PLAN §3 calls non-negotiable: every retrieved chunk with its score.
 */
@Component
@ConditionalOnProperty("sidekick.ask.question")
class AskCli implements ApplicationRunner {

    private final AnswerService answers;
    private final String question;
    private final String fundId;

    AskCli(AnswerService answers,
           @Value("${sidekick.ask.question}") String question,
           @Value("${sidekick.ask.fund}") String fundId) {
        this.answers = answers;
        this.question = question;
        this.fundId = fundId;
    }

    @Override
    public void run(ApplicationArguments args) {
        Answer answer = answers.ask(question, fundId, fundId);

        System.out.println("\n================ SIDekick ================");
        System.out.println("Q: " + question + "   [fund: " + fundId + "]");
        System.out.println("Outcome: " + answer.outcome());
        System.out.println("\n" + answer.text());
        if (!answer.citations().isEmpty()) {
            System.out.println("\nCitations:");
            answer.citations().forEach(c -> System.out.printf("  [%d] %s, pages %d-%d%n",
                    c.excerptNumber(), c.section(), c.page(), c.endPage()));
        }
        if (!answer.validationProblems().isEmpty()) {
            System.out.println("\nGate-2 validation problems (why this became a refusal):");
            answer.validationProblems().forEach(p -> System.out.println("  - " + p));
        }
        System.out.println("\n--- debug: retrieved chunks ---");
        for (int i = 0; i < answer.retrieved().size(); i++) {
            var chunk = answer.retrieved().get(i);
            System.out.printf("  [%d] %.4f  p.%d-%d  %s%n", i + 1, chunk.score(),
                    chunk.page(), chunk.endPage(), chunk.section());
        }
    }
}
