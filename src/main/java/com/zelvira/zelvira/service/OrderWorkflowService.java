package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.repository.OrderRepository;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderWorkflowService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final OrderRepository orderRepository;

    public OrderWorkflowService(RuntimeService runtimeService, TaskService taskService,
            OrderRepository orderRepository) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.orderRepository = orderRepository;
    }

    public void startOrderProcess(Order order, String customerUsername) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("orderNumber", order.getOrderNumber());
        variables.put("customerUsername", customerUsername);

        runtimeService.startProcessInstanceByKey("order-process", String.valueOf(order.getId()), variables);
    }

    public List<Map<String, Object>> getAdminTasks() {
        return taskService.createTaskQuery()
                .taskCandidateGroup("ADMIN")
                .list()
                .stream()
                .map(this::mapTaskToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTasksByGroups(List<String> groups) {
        if (groups == null || groups.isEmpty())
            return List.of();
        return taskService.createTaskQuery()
                .taskCandidateGroupIn(groups)
                .list()
                .stream()
                .map(this::mapTaskToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUserTasks(String username) {
        return taskService.createTaskQuery()
                .taskAssignee(username)
                .list()
                .stream()
                .map(this::mapTaskToMap)
                .collect(Collectors.toList());
    }

    public void completeTask(String taskId, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null) {
            Map<String, Object> currentVars = taskService.getVariables(taskId);
            Object orderIdObj = currentVars.get("orderId");
            if (orderIdObj != null) {
                Long orderId = Long.valueOf(orderIdObj.toString());
                Order order = orderRepository.findById(orderId).orElse(null);
                if (order != null) {
                    if (variables != null && variables.containsKey("isApproved")) {
                        boolean isApproved = Boolean.parseBoolean(variables.get("isApproved").toString());
                        if (isApproved) {
                            order.setStatus("PROCESSING");
                        } else {
                            order.setStatus("CANCELLED");
                        }
                        orderRepository.save(order);
                    } else if (variables != null && variables.containsKey("trackingNumber")) {
                        order.setTrackingNumber(variables.get("trackingNumber").toString());
                        order.setStatus("DELIVERING");
                        orderRepository.save(order);
                    }
                }
            }
        }
        taskService.complete(taskId, variables);
    }

    private Map<String, Object> mapTaskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("name", task.getName());
        map.put("processInstanceId", task.getProcessInstanceId());
        map.put("createTime", task.getCreateTime());

        Map<String, Object> variables = new HashMap<>(taskService.getVariables(task.getId()));
        Object orderIdObj = variables.get("orderId");
        if (orderIdObj != null) {
            try {
                Long orderId = Long.valueOf(orderIdObj.toString());
                orderRepository.findById(orderId).ifPresent(order -> {
                    variables.put("orderNumber", order.getOrderNumber());
                    variables.put("totalAmount", order.getTotalAmount());
                    variables.put("status", order.getStatus());
                    variables.put("paymentMethod", order.getPaymentMethod());
                    variables.put("shippingRecipient", order.getShippingRecipient());
                    variables.put("shippingPhone", order.getShippingPhone());
                    variables.put("shippingAddress", order.getShippingAddress());
                    variables.put("trackingNumber", order.getTrackingNumber());

                    int totalQty = 0;
                    if (order.getItems() != null) {
                        totalQty = order.getItems().stream()
                                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                                .sum();
                    }
                    variables.put("totalQty", totalQty);

                    if (order.getUser() != null) {
                        String name = order.getUser().getFullName();
                        if (name == null || name.isBlank()) {
                            name = order.getUser().getEmail();
                        }
                        variables.put("customerName", name);
                    } else if (order.getShippingRecipient() != null) {
                        variables.put("customerName", order.getShippingRecipient());
                    } else {
                        variables.put("customerName", variables.get("customerUsername"));
                    }
                });
            } catch (Exception ignored) {
            }
        }

        map.put("variables", variables);
        return map;
    }
}
