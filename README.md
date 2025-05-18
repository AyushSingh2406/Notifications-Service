# 🔔 Notification Service System

This is a Notification Service built using **Spring Boot**, **MySQL**, and **RabbitMQ**. It sends notifications to users through **Email**, **SMS**, and **In-App** messages with support for **retry logic** and **queue-based asynchronous processing**.

---

## 📸 Screenshots (Replace with your images)

1. **FOR EMAIL(POST) POSTMAN**
   
![project1](https://github.com/user-attachments/assets/e6a07873-f544-4acd-bc2d-6ee344dcc8ca)

2. **GET**

 ![project2](https://github.com/user-attachments/assets/4ee2ed1e-914e-4258-8b2b-89bd51eceead)


 **TERMINAL**

 ![emailterminal](https://github.com/user-attachments/assets/b214b0e9-dbb0-434e-bebc-0370132ab873)



3. **FOR SMS(POST) POSTMAN**
   
![projectsms1](https://github.com/user-attachments/assets/0f2f3d69-7cd0-4785-97eb-6de5db077096)


4. **GET**

![sms2](https://github.com/user-attachments/assets/2a69d13c-f255-433a-a317-57c0b9fbb337)

 **TERMINAL**

![smsterminal](https://github.com/user-attachments/assets/521fd2b8-7054-42ff-8879-11be2d4fe479)



5. **FOR IN_APP(POST) POSTMAN**

![inapp1](https://github.com/user-attachments/assets/1cda83b9-01d3-44d4-a91b-78cf16c766f5)



6. **GET**

![inapp2](https://github.com/user-attachments/assets/455fa8df-93fa-44c7-8df2-0ceed9b590b9)


 **TERMINAL**

![inappterminal](https://github.com/user-attachments/assets/4989bb37-5dfc-44b2-a30b-3bbe9dce6f55)


 
7. **RETRY TEST CASE(ATTEMPT ONE)**

![attempt1retry](https://github.com/user-attachments/assets/19b62716-8c41-4dbf-972c-dacfa11d21a5)


8.  **RETRY TEST CASE(ATTEMPT TWO)**

![retry2](https://github.com/user-attachments/assets/fe107a78-6720-4648-9ade-8f992cd4d788)


9. **RETRY TEST CASE(ATTEMPT THREE SUCCESS)**

![attempt3](https://github.com/user-attachments/assets/d64e7683-a37e-4a14-a8f3-1b4ad2b149a4)

10. **GET AFTER RETRY(POSTMAN)**
  
![retrysuccess](https://github.com/user-attachments/assets/66e89356-c854-4cff-9b51-1998fcf912ed)



11. **RABBITMQ INTERFACE**


![rabbitmq](https://github.com/user-attachments/assets/c450214c-f4a0-4613-90ee-8e1538d32c0d)


12. **MYSQL ENTRIES**

![database](https://github.com/user-attachments/assets/d7781b23-6599-4c37-9c36-afb5351aebd6)


---

## 🧱 Tech Stack

- Spring Boot 3.2.6
- Spring Data JPA
- Spring AMQP (RabbitMQ)
- Spring Retry
- MySQL
- Maven
- Lombok

---

---

## ⚙️ Prerequisites

Make sure the following are installed:

- Java 17+
- Maven
- MySQL
- RabbitMQ Server
- Postman (for testing)

---

## 🔧 Configuration

Update your `application.properties`:

```properties
# MySQL Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/notification_db
spring.datasource.username=root
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update

# RabbitMQ Configuration
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Retry Configuration
notification.max-retries=3
notification.retry-delay=5000
```
---

## 🐇 RabbitMQ Setup

1. **Run RabbitMQ Server**
   - Default port: `localhost:5672`

2. **Access RabbitMQ Management UI**
   - URL: [http://localhost:15672](http://localhost:15672)
   - Login with:
     - **Username**: `guest`
     - **Password**: `guest`

3. **Create a Queue**
   - Queue Name: `notification_queue`

---

## ▶️ Running the App

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```
## 📬 API Endpoint
```json
POST /api/notifications
Create a new notification

Request Body:
{
  "userId": 101,
  "message": "Welcome to our service!",
  "notificationType": "EMAIL"
}

Sample Response:
{
  "id": 1,
  "userId": 101,
  "message": "Welcome to our service!",
  "status": "PENDING",
  "notificationType": "EMAIL"
}
```
## 🧠 How It Works
- You call an API to create a notification.

- It is stored in MySQL with PENDING status.

- The ID is sent to the RabbitMQ queue.

- A RabbitMQ listener picks it up and processes it.

- Based on type (EMAIL, SMS, IN_APP), it sends the message.

- If successful, status is updated to SENT. If failed, it retries.

- After all retries fail, status is updated to FAILED.

## 🔄 Retry Logic
Configurable in application.properties

- properties
- Copy
- Edit
- notification.max-retries=3
- notification.retry-delay=5000
- Automatically retries when exceptions occur.

## 📄 Important Java Files
Notification Entity
```java
@Entity
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status = NotificationStatus.PENDING;

    private int retryCount = 0;
    private LocalDateTime lastAttempt;
}
```
Notification Processor (Listener)
```
java
Copy
Edit
@RabbitListener(queues = RabbitMQConfig.QUEUE)
@Retryable(
  maxAttemptsExpression = "${notification.max-retries}",
  backoff = @Backoff(delayExpression = "${notification.retry-delay}")
)
public void processNotification(Long notificationId) {
    Notification notification = repository.findById(notificationId)
        .orElseThrow(() -> new RuntimeException("Not found"));

    try {
        boolean sent = switch (notification.getNotificationType()) {
            case EMAIL -> sendEmail(notification);
            case SMS -> sendSms(notification);
            case IN_APP -> sendInApp(notification);
        };

        if (sent) {
            notification.setStatus(NotificationStatus.SENT);
        } else {
            throw new RuntimeException("Send failed");
        }
    } catch (Exception e) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setRetryCount(notification.getRetryCount() + 1);
        throw e;
    }

    notification.setLastAttempt(LocalDateTime.now());
    repository.save(notification);
}
```
## ✅ Future Improvements
- Email integration with SMTP

- SMS service via Twilio or Fast2SMS

- Dashboard UI for notifications

- Swagger documentation

- Docker support for RabbitMQ + MySQL + App

