workspace {

  model {
    buyer = person "Buyer" "Browses products, adds to cart, places orders"
    seller = person "Seller" "Manages product catalog"

    marketplace = softwareSystem "Marketplace" {

      webapp = container "Web/Mobile App" "UI for buyers and sellers" "SPA"
      gateway = container "API Gateway" "Entry point, routing" "Nginx"

      userService = container "User Service" "Users and roles" "FastAPI"
      catalogService = container "Catalog Service" "Product catalog management" "FastAPI"
      feedService = container "Feed Service" "Personalized feed" "FastAPI"
      cartService = container "Cart Service" "Cart management" "FastAPI"
      orderService = container "Order Service" "Order lifecycle" "FastAPI"
      paymentService = container "Payment Service" "Payment accounting" "FastAPI"
      notificationService = container "Notification Service" "Sends notifications" "FastAPI"

      userDb = container "User DB" "Users and roles" "PostgreSQL"
      catalogDb = container "Catalog DB" "Products" "PostgreSQL"
      cartDb = container "Cart DB" "Carts" "PostgreSQL"
      orderDb = container "Order DB" "Orders" "PostgreSQL"
      paymentDb = container "Payment DB" "Payments" "PostgreSQL"

      kafka = container "Kafka" "Event bus" "Kafka"
      redis = container "Redis" "Cache" "Redis"
    }

    buyer -> webapp
    seller -> webapp
    webapp -> gateway

    gateway -> userService
    gateway -> catalogService
    gateway -> feedService
    gateway -> cartService
    gateway -> orderService
    gateway -> paymentService

    userService -> userDb
    catalogService -> catalogDb
    cartService -> cartDb
    orderService -> orderDb
    paymentService -> paymentDb

    orderService -> kafka
    paymentService -> kafka
    notificationService -> kafka

    feedService -> redis
    catalogService -> redis
  }

  views {
    container marketplace {
      include *
      autolayout lr
    }
  }
}
