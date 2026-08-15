# LifeFlow Presentation Script

Target duration: 15 minutes. Use fictional demonstration data.

## Slide sequence

1. **Title and SDG 3** - Introduce LifeFlow and the educational-simulation scope.
2. **Problem** - Explain limited stock, expired units, and competing requests.
3. **Objectives** - Summarise donor, inventory, request, matching, and persistence goals.
4. **Workflow** - Donor -> blood unit -> request -> priority -> match -> save.
5. **Architecture** - Explain model, service, persistence, and Swing UI packages.
6. **OOP Design** - Show the request hierarchy and runtime polymorphism.
7. **Matching Logic** - Explain exact blood group, availability, expiry, and quantity.
8. **Collections and File I/O** - Show ArrayList, HashMap, and text schemas.
9. **Testing and Demo** - Present automated tests and run the live scenario.
10. **Limitations and Conclusion** - State the medical limitations and SDG value.

## Suggested team allocation

- Member 1: slides 1-2 and SDG problem.
- Member 2: slides 3-4 and system workflow.
- Member 3: slides 5-6 and OOP/UML.
- Member 4: slides 7-8 and matching/persistence.
- Member 5: slides 9-10, live demo, limitations, and conclusion.

## Live-demo checklist

1. Start with empty or known fictional data.
2. Add an eligible donor.
3. Add two matching blood units.
4. Create a regular request and then an emergency request.
5. Process the next request and identify the emergency priority.
6. Show the used units and fulfilled status.
7. Restart the application and confirm persistence.
