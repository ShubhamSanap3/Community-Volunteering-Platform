package com.example.volunteer_platform.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.volunteer_platform.dto.TaskDto;
import com.example.volunteer_platform.dto.TaskPartialDto;
import com.example.volunteer_platform.model.*;
import com.example.volunteer_platform.service.TaskSignupService;
import com.example.volunteer_platform.service.UserService;
import com.example.volunteer_platform.service.TaskService;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * TaskController handles API endpoints for managing tasks within the volunteer platform.
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private TaskSignupService taskSignupService;

    /**
     * Get all tasks posted by any organization.
     *
     * @return List of tasks or HTTP 204 if no tasks exist.
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getAllTasks() {
        List<Task> tasks = taskService.getAllTasks();

        if (tasks.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    /**
     * Get a task by its ID.
     *
     * @param taskId Task ID.
     * @return Task details or HTTP 404 if not found.
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Task> getTaskById(@PathVariable Long taskId) {
        Task task = taskService.findById(taskId).orElse(null);
        if (task != null) {
            return new ResponseEntity<>(task, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // GET or Search tasks by name, location, or description (will implement later)
//    @GetMapping("/search")
//    public ResponseEntity<List<Task>> searchTasks(
//            @RequestParam(required = false) String title,
//            @RequestParam(required = false) String location,
//            @RequestParam(required = false) String description) {
//
//        List<Task> tasks = taskService.searchTasks(title, location, description);
//
//        if (tasks.isEmpty()) {
//            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//        }
//
//        return new ResponseEntity<>(tasks, HttpStatus.OK);
//    }


    /**
     * Delete a task by its ID (Admin only).
     *
     * @param taskId Task ID.
     * @return HTTP 204 if deleted, HTTP 404 if not found.
     */
    @DeleteMapping("/tasks/delete/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId) {
        Optional<Task> task = taskService.findById(taskId);
        if (task.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        List<TaskSignup> signups = taskSignupService.getTaskSignups(taskId);

        for (TaskSignup signup : signups) {
            taskSignupService.deleteById(signup.getSignupId());
        }

        taskService.deleteByTaskId(taskId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // Organization Tasks APIs
    /**
     * Get all tasks for a specific organization.
     *
     * @param organizationId Organization ID.
     * @return List of tasks for the organization or HTTP 404 if not found.
     */
    @GetMapping("/organizations/{organizationId}/tasks")
    public ResponseEntity<List<Task>> getOrganizationTasks(@PathVariable Long organizationId) {
        Optional<Organization> organizationOpt = userService.findOrganizationById(organizationId);
        if (organizationOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Organization organization = organizationOpt.get();
        return new ResponseEntity<>(organization.getTasks(), HttpStatus.OK);
    }

    /**
     * Add a new task to an organization.
     *
     * @param organizationId Organization ID.
     * @param taskDto Task details.
     * @return Created task or HTTP 400 for invalid input.
     */
    @PostMapping("/organizations/{organizationId}/tasks")
    public ResponseEntity<Organization> addTaskToOrganization(@PathVariable Long organizationId, @RequestBody @Valid TaskDto taskDto) {
        Optional<Organization> organizationOpt = userService.findOrganizationById(organizationId);
        if (organizationOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        if (taskDto.getApplicationDeadline().isBefore(LocalDate.now()) ||
                taskDto.getCancellationDeadline().isBefore(LocalDate.now())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (taskDto.getApplicationDeadline().isAfter(taskDto.getEventDate()) ||
                taskDto.getCancellationDeadline().isAfter(taskDto.getEventDate())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Organization organization = organizationOpt.get();
        Task task = new Task();
        try {
            task.setTitle(taskDto.getTitle());
            task.setDescription(taskDto.getDescription());
            task.setLocation(taskDto.getLocation());
            task.setEventDate(taskDto.getEventDate());
            task.setCancellationDeadline(taskDto.getCancellationDeadline());
            task.setApplicationDeadline(taskDto.getApplicationDeadline());
            taskService.saveTask(task);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        organization.getTasks().add(task);
        userService.saveUser(organization);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    /**
     * Update an existing task in an organization.
     *
     * @param organizationId Organization ID.
     * @param taskId Task ID.
     * @param updatedTask TaskPartialDto updatedTask details.
     * @return Updated organization or HTTP 404 if not found.
     */
    @PutMapping("/organizations/{organizationId}/tasks/{taskId}")
    public ResponseEntity<Organization> updateTaskInOrganization(@PathVariable Long organizationId, @PathVariable Long taskId, @RequestBody @Valid TaskPartialDto updatedTask) {
        Optional<Organization> organizationOpt = userService.findOrganizationById(organizationId);
        if (organizationOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Organization organization = organizationOpt.get();
        Task existingTask = taskService.findById(taskId).orElse(null);
        if (existingTask == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        try {
            existingTask.setTitle(updatedTask.getTitle() != null ? updatedTask.getTitle() : existingTask.getTitle());
            existingTask.setDescription(updatedTask.getDescription() != null ? updatedTask.getDescription() : existingTask.getDescription());
            existingTask.setLocation(updatedTask.getLocation() != null ? updatedTask.getLocation() : existingTask.getLocation());
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        taskService.saveTask(existingTask);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    /**
     * Delete a task from an organization.
     *
     * @param organizationId Organization ID.
     * @param taskId Task ID.
     * @return Updated organization or HTTP 404 if not found.
     */
    @DeleteMapping("/organizations/{organizationId}/tasks/{taskId}")
    public ResponseEntity<Organization> deleteTaskInOrganization(@PathVariable Long organizationId, @PathVariable Long taskId) {
        Optional<Organization> organizationOpt = userService.findOrganizationById(organizationId);
        if (organizationOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Organization organization = organizationOpt.get();
        Task existingTask = taskService.findById(taskId).orElse(null);
        if (existingTask == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        List<TaskSignup> signups = taskSignupService.getTaskSignups(taskId);

        for (TaskSignup signup : signups) {
            taskSignupService.deleteById(signup.getSignupId());
        }

        organization.getTasks().remove(existingTask);
        userService.saveUser(organization);
        taskService.deleteByTaskId(taskId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }
}
