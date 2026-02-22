workspace "Marketplace" "C4 model for marketplace (SOA) with Kafka and observability" {

  model {
    buyer = person "Buyer" "Browses products, adds to cart, places orders"
    seller = person "Seller" "Manages product catalog"

    marketplace = softwareSystem "Marketplace" "Digital platform for buyers and sellers" {

      webapp = container "Web/Mobile App" "UI for buyers and sellers" "Browser/Mobile"
      gateway = container "API Gateway" "Single entry point (routing, throttling), request logging" "Nginx/Traefik"

      userService = container "User Service" "Users (buyers/sellers), preferences for feed" "REST (FastAPI)"
      catalogService = container "Catalog Service" "Product catalog management (seller) + read API" "REST (FastAPI)"
      feedService = container "Feed Service" "Personalized feed (rule-based), uses cache" "REST (FastAPI)"
      cartService = container "Cart Service" "Cart. Only cart can create an order" "REST (FastAPI)"
      orderService = container "Order Service" "Order lifecycle. Publishes order events" "REST + Kafka consumer"
      paymentService = container "Payment Service" "Payments accounting, timeouts (expires_at)" "REST + Kafka consumer"
      notificationService = container "Notification Service" "Notifies about order status changes (mock)" "Kafka consumer"

      kafka = container "Kafka" "Async event bus" "Kafka"
      redis = container "Redis" "Cache for feed/catalog reads" "Redis"

      userDb = container "User DB" "Users, roles, preferences" "PostgreSQL"
      catalogDb = container "Catalog DB" "Products, prices, stock" "PostgreSQL"
      cartDb = container "Cart DB" "Carts and items" "PostgreSQL"
      orderDb = container "Order DB" "Orders and items" "PostgreSQL"
      paymentDb = container "Payment DB" "Payments, attempts, expires_at" "PostgreSQL"

      prometheus = container "Prometheus" "Metrics scraping" "Prometheus"
      grafana = container "Grafana" "Dashboards" "Grafana"
    }

    buyer -> webapp "Uses"
    seller -> webapp "Uses"
    webapp -> gateway "HTTPS"

    gateway -> feedService "REST"
    gateway -> catalogService "REST"
    gateway -> cartService "REST"
    gateway -> orderService "Reads order status" "REST"
    gateway -> paymentService "REST"
    gateway -> userService "REST"

    userService -> userDb "Read/Write"
    catalogService -> catalogDb "Read/Write"
    cartService -> cartDb "Read/Write"
    orderService -> orderDb "Read/Write"
    paymentService -> paymentDb "Read/Write"

    feedService -> redis "Cache read/write"
    catalogService -> redis "Cache read/write"

    feedService -> userService "Reads preferences/signals" "REST"
    feedService -> catalogService "Reads products" "REST"

    cartService -> catalogService "Validates product/price (optional)" "REST"
    cartService -> orderService "Creates order from cart (only path)" "REST"

    orderService -> kafka "Publishes OrderCreated/OrderStatusChanged" "Async"
    paymentService -> kafka "Consumes OrderCreated" "Async"
    paymentService -> kafka "Publishes PaymentSucceeded/PaymentExpired" "Async"
    orderService -> kafka "Consumes payment events" "Async"
    notificationService -> kafka "Consumes OrderStatusChanged" "Async"

    prometheus -> userService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> catalogService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> feedService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> cartService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> orderService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> paymentService "Scrapes /metrics" "HTTP (pull)"
    prometheus -> notificationService "Scrapes /metrics" "HTTP (pull)"
    grafana -> prometheus "Queries metrics"
  }

  views {
    systemContext marketplace "SystemContext" {
      include *
      autolayout lr
    }

    container marketplace "Container" {
      include *
      autolayout lr
    }

    styles {
      element "Person" { shape Person }
      element "Database" { shape Cylinder }
    }

    theme default
  }
}
