# Sway system

Gun sway is random bullet direction deviation depending
on player's current motion:
- Penalize accuracy when moving, sprinting, riding horse, etc.
- Increase accuracy when sneaking, staying still, etc.

## Motion detection system
Mineman player **velocity is actually acceleration**. 
We cannot directly get velocity from an entity, so instead we
need a periodic tick that measures velocity as change in player's
position over a tick interval. 

# TODO: describe threaded player velocity calculations