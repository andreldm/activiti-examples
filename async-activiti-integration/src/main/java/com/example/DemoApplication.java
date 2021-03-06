package com.example;

import java.util.Collections;
import java.util.Map;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.bpmn.behavior.ReceiveTaskActivityBehavior;
import org.activiti.engine.impl.delegate.ActivityBehavior;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class DemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	private Log log = LogFactory.getLog(getClass());

	@Bean
	ActivityBehavior gateway(MessageChannels channels) {
		return new ReceiveTaskActivityBehavior() {
			private static final long serialVersionUID = 6385310958929814599L;

			@Override
			public void execute(DelegateExecution execution) {
				log.info("Entered the gateway. Execution id is " + execution.getId());

				Message<?> executionMessage = MessageBuilder.withPayload(execution)
						.setHeader("executionId", execution.getId()).build();

				channels.requests().send(executionMessage);
			}
		};
	}

	@Bean
	IntegrationFlow requestsFlow(MessageChannels channels) {
		return IntegrationFlows.from(channels.requests())
				.handle(msg -> msg.getHeaders().entrySet()
						.forEach(e -> log.info("[Request channel] Message received " + e.getKey() + '=' + e.getValue())))
				.get();
	}

	@Bean
	IntegrationFlow repliesFlow(MessageChannels channels, ProcessEngine engine) {
		return IntegrationFlows.from(channels.replies()).handle(msg -> engine.getRuntimeService().trigger(String.class.cast(msg.getHeaders().get("executionId"))))
				.get();
	}
}

@Configuration
class MessageChannels {
	@Bean
	DirectChannel requests() {
		return new DirectChannel();
	}

	@Bean
	DirectChannel replies() {
		return new DirectChannel();
	}
}

@RestController
class ProcessStartingRestController {

	@Autowired
	private ProcessEngine processEngine;

	@RequestMapping(method = RequestMethod.GET, value = "/start")
	Map<String, String> launch() {
		ProcessInstance asyncProcess = this.processEngine.getRuntimeService().startProcessInstanceByKey("asyncProcess");

		String executionId = processEngine.getRuntimeService()
				.createExecutionQuery()
				.activityId("sigw")
				.singleResult()
				.getId();

		return Collections.singletonMap("executionId", executionId);
	}
}

@RestController
class ProcessResumingRestController {

	@Autowired
	private MessageChannels messageChannels;

	@RequestMapping(method = RequestMethod.GET, value = "/resume/{executionId}")
	void resume(@PathVariable String executionId) {
		Message<String> build = MessageBuilder.withPayload(executionId).setHeader("executionId", executionId).build();
		this.messageChannels.replies().send(build);
	}
}
