# Assumptions about projectile system
1.  Players are shooting at each other, so bullets will have
    high spatial locality in similar chunks.
2.  Most bullets are traveling through air blocks.

Since bullets should have high spatial locality, try to coalesce
as many chunk related checks (`getEntities`, calculate hitboxes, etc.)
into single step. This projectile system will first calculate all
possible chunks all bullets may visit, then build 3d chunk `(cx, cy, cz)`
structure to store potential entity hitbox.

Since projectiles usually in air, can use coarse collision test
that steps 1 block until non-air material found. So for a bullet
that travels 20 blocks/tick, and everything is air except 1 wall
at the end, we should simplify this into 19 air block checks then
1 fine non-air block check with multi-steps. 


# Rough overview of projectile loop steps
1.  **Update projectile dynamics:** Calculate `start` and `stop`
    coordinates and new velocity/direction. Use physics 101
    dynamics `r1 = r0 + v0*t + a*t^2` to calculate endpoints...
    but actual bullet motion is just linearized raytrace from
    `r0` to `r1`.

2.  **Build set of 2D `(x, z)` chunks visited by all projectiles.**
    This is used to gather all possible entity hitboxes.
    Do this by getting 2D AABB `r0` and `r1` previously calculated.
    Also, add additional buffer to account for entity hitboxes
    that span chunks (currently hardcoded to 8). This looks like:
    ```
    buffer = 8
    cxmin = (min(r0.x, r1.x) - buffer) >> 4
    czmin = (min(r0.z, r1.z) - buffer) >> 4
    cxmax = (max(r0.x, r1.x) + buffer) >> 4
    czmax = (max(r0.z, r1.z) + buffer) >> 4
    ```

3.  **Create map of 3D chunk `(x, y, z) => List<Hitbox>`.**
    With the previously generated visited chunks `Set<(x, z)>`,
    get all entities and insert them into all 3D chunks that 
    intersect with their hitbox AABB:
    ```
    hitboxes = Map<(x, y, z), Hitbox>

    for (cx, cz) in visitedChunks:
        entities = world.getEntities(cx, cz)
        for entity in entities:
            if entity.canBeTargeted():
                hitbox = Hitbox.from(entity)
                for (hx, hy, hz) in getIntersecting3DChunks(hitbox)
                    hitboxes[(hx, hy, hz)].push(hitbox)
    ```
    This filters valid entities in chunks (removing items, etc.)
    through `canBeTargeted` and maps valid entities to 3D chunk coords
    which will have better spatial locality for projectile checks.

    Hitbox is just a reference to an entity and a 3D AABB.
    Here there is flexibility for client to derive `Hitbox.from(entity)`.
    For ArmorStands custom models (like vehicles), hitboxes can
    be specially handled to be custom sized, without affecting
    the rest of this projectile system.
    
4.  **Run bullet block raytrace and create ordered list of chunks visited.**
    This uses [Amanatides & Woo voxel traversal algorithm](
    http://www.cse.yorku.ca/~amana/research/grid.pdf)
    to trace blocks from starting coord `r0` to ending coord `r1`.
    This tests if a block collision is detected. And gather a list of
    chunks visited in order, from each time `block.getChunk()` changes.
    
    For non-air blocks, run a material-specific fine raymarching hit
    test at the start and stop coords for that block step.
    This can be as simple as a single hardcoded true for some blocks
    (like glass or leaves that we want bullets to pass through).
    For complicated blocks like stairs, this handler will need to
    check the block data (e.g. stair configuration) then do fine sub-block
    steps and check collision at each point.

    Issue with algorithm is that between bullet trace steps, this can
    cause diagonal voxel steps. This can allow bullets to phase through
    diagonal walls. This can be reduced by over-testing blocks along
    the path, e.g. instead ending at `N` blocks, do additional `N+1`, `N+2`,... 
    which will reduce possible angles that bullets can pass through
    diagonal walls.

5.  **Run entity hitbox raycasting.**
    In previous bullet block raytrace, we generated a list of chunks visited
    `bulletChunksVisited: List<(x, y, z)>`. For each chunk, get entities in
    pre-generated `hitboxes: Map<(x, y, z), Hitbox>` and run raycast on each
    entity hitbox AABB. This uses the slab method for ray-AABB collision test:
    - <https://tavianator.com/2011/ray_box.html>
    - <https://tavianator.com/cgit/dimension.git/tree/libdimension/bvh/bvh.c#n196>

6.  **Return shorter distance block hit or entity hit.**
    The bullet raytrace and entity hitbox raytrace both return a distance along
    ray to the hit target. Return the shorter of the two hits (if exist).
    If only one or other exists, return that.

7.  **Async particles and block cracking animation.**
    Projectile update loop pushes all particle and aesthetic packets
    into request queues. After projectile system updates, particles and
    other packets are  scheduled into async tasks. This includes calculating
    bullet trails, block hit particles, block random cracking animations, etc.