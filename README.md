# Box2DLights
[![screenshot](http://img.youtube.com/vi/lfT8ajGbzk0/0.jpg)](http://www.youtube.com/watch?v=lfT8ajGbzk0)

Kalle Hameleinen's Box2DLights is a 2D lighting framework that uses [box2d](http://box2d.org/) for raycasting and OpenGL ES 2.0 for rendering. This library is intended to be used with [libgdx](http://libgdx.com).

FEATURES:
 * Arbitrary number of lights
 * Gaussian blurred light maps
 * Point light
 * Cone Light
 * Directional Light
 * Shadows
 * Dynamic/static/xray light
 * Culling
 * Colored ambient light
 * Gamma corrected colors
 * Handler class to do all the work
 * Query method for testing is point inside of light/shadow

This library offer easy way to add soft dynamic 2d lights to your physic based game. Rendering use libgdx but it would be easy to port this to other frameworks or pure openGl too.

Try Kalle's game [Boxtrix](https://market.android.com/details?id=boxtrix.android) to see the library in action.

## Usage
TBD

## Maintenance Note
Box2dlights was moved from Google Code to Github to make contributing easier. The libgdx team will happily merge pull requests but will not fix bugs or ensure compatibility with the latest libgdx version.

