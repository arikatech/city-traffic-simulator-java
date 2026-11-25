# Smart City Traffic Simulator

An extensible, object-oriented traffic simulation system developed in Java 21. The simulator models urban traffic dynamics, vehicle interactions, pedestrian movement, traffic light management, environmental events, and customizable traffic regulations through an interactive graphical interface.

## Features

- **Traffic simulation:** Simulates cars, buses, trucks, emergency vehicles, and pedestrians.
- **Urban infrastructure:** Models roads, lanes, intersections, traffic lights, and pedestrian crossings.
- **Dynamic routing:** Uses breadth-first search (BFS) for route planning and supports dynamic rerouting.
- **Vehicle interactions:** Handles safe vehicle spacing, movement coordination, speed restrictions, and intersection management.
- **Traffic scenarios:** Supports normal traffic, rush hour, rain, roadwork, and accidents.
- **Visual rule builder:** Allows users to create, modify, enable, disable, and remove traffic regulations at runtime.
- **Event scheduling:** Simulates environmental changes and traffic incidents.
- **Real-time statistics:** Displays traffic metrics, average vehicle speeds, and completed trips.
- **Extensible architecture:** Supports the introduction of new vehicle types, events, scenarios, rule conditions, and actions.

## Technologies

| Technology | Purpose |
|---|---|
| Java 21 | Core application development |
| Java Swing | Graphical user interface |
| Maven | Build and dependency management |
| NetBeans | Development environment |
| Git | Version control |

## System Architecture

The application is organized into modular components with clearly separated responsibilities.

| Component | Responsibility |
|---|---|
| City Infrastructure | Roads, lanes, intersections, and crossings |
| Entities and Vehicles | Mobile entities and vehicle-specific behavior |
| Simulation Engine | Simulation lifecycle and movement coordination |
| Routing | Route planning and dynamic rerouting |
| Rule Engine | Traffic regulation evaluation and effect resolution |
| Events | Scheduled incidents and environmental changes |
| Scenarios | Traffic generation and predefined configurations |
| GUI | Visualization, simulation controls, and rule customization |

The architecture separates simulation logic from graphical presentation and provides abstraction layers for extending system behavior.

## Customizable Rule Engine

One of the central features of the simulator is its runtime traffic regulation system.

Each rule consists of three main components:

- **Target Scope:** Determines which simulation objects are considered.
- **Condition:** Determines whether a rule should be applied.
- **Action:** Defines the effect applied when the condition is satisfied.

Example regulations include:

- Reduce vehicle speed by 20% during rainy weather.
- Apply speed restrictions to vehicles on a specific road.
- Request intersection priority for emergency vehicles.
- Close selected roads under specified conditions.

Rules can be configured through the graphical interface without modifying the source code or restarting the simulation.

## Extensibility

The system is designed to support new functionality through its existing abstractions and extension mechanisms.

Developers can introduce:

- New vehicle and mobile entity types.
- New scheduled simulation events.
- New traffic scenarios.
- Custom rule conditions.
- Custom rule actions.

This approach minimizes changes to existing core simulation logic and promotes maintainability and modularity.

## Object-Oriented Design

The project demonstrates practical applications of object-oriented programming and software engineering principles, including:

- Encapsulation and information hiding
- Abstraction
- Inheritance
- Subtyping and multityping
- Runtime polymorphism
- Interfaces and generic types
- Method overriding and overloading
- Composition and aggregation
- Delegation
- Exception handling
- Separation of concerns
- SOLID design principles

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 21
- Apache Maven
- NetBeans or another Java-compatible IDE

### Installation

Clone the repository:

```bash
git clone https://github.com/YOUR_USERNAME/smart-city-traffic-simulator.git
cd smart-city-traffic-simulator
```

Build the project:

```bash
mvn clean package
```

### Running the Application

1. Open NetBeans.
2. Select File > Open Project.
3. Select the cloned project directory.
4. Build and run the project.

The graphical interface allows users to select traffic scenarios, control the simulation, monitor traffic statistics, and create customized traffic regulations.

## Future Improvements

- Adaptive traffic light optimization.
- Machine learning-based traffic prediction.
- Integration with real-world geographic data.
- Larger urban network simulations.
- Advanced traffic analytics and reporting.

## Academic Context

Developed as part of the Object-Oriented Programming course at the University of Messina, with an emphasis on modular architecture, extensibility, customization, and practical object-oriented software design.
