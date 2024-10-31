# Box2DLights

[![GitHub Actions Build Status](https://img.shields.io/github/actions/workflow/status/libgdx/box2dlights/main.yml?branch=master&label=GitHub%20Actions)](https://github.com/libgdx/box2dlights/actions?query=workflow%3A%22Build+and+deploy%22)

[![Latest Version](https://img.shields.io/nexus/r/com.badlogicgames.box2dlights/box2dlights?nexusVersion=2&server=https%3A%2F%2Foss.sonatype.org&label=Version)](https://search.maven.org/artifact/com.badlogicgames.box2dlights/box2dlights)
[![Snapshots](https://img.shields.io/nexus/s/com.badlogicgames.box2dlights/box2dlights?server=https%3A%2F%2Foss.sonatype.org&label=Snapshots)](https://oss.sonatype.org/#nexus-search;gav~com.badlogicgames.box2dlights~box2dlights~~~~kw,versionexpand)

[![screenshot](http://img.youtube.com/vi/lfT8ajGbzk0/0.jpg)](http://www.youtube.com/watch?v=lfT8ajGbzk0)

Kalle Hameleinen's Box2DLights is a 2D lighting framework that uses [box2d](http://box2d.org/) for raycasting and OpenGL ES 2.0 for rendering. This library is intended to be used with [libgdx](http://libgdx.com).

Try Kalle's game [Boxtrix](https://market.android.com/details?id=boxtrix.android) to see the library in action.

## Features

 * Arbitrary number of lights
 * Gaussian blurred light maps
 * Point light
 * Cone Light
 * Directional Light
 * Chain Light [New in 1.3]
 * Shadows
 * Dynamic/static/xray light
 * Culling
 * Colored ambient light
 * Gamma corrected colors
 * Handler class to do all the work
 * Query method for testing is point inside of light/shadow

This library offer easy way to add soft dynamic 2d lights to your physic based game. Rendering use libgdx, but it would be easy to port this to other frameworks or pure openGl too.

## Usage
 * Download the [latest Box2DLights release](http://libgdx.badlogicgames.com/box2dlights/)
 * Add the box2dlights.jar file to your libgdx core project's classpath
 * Check out the [Wiki](https://github.com/libgdx/box2dlights/wiki)

Box2DLights is also available in Maven Central. Add the following dependency to your libgdx core project:

    <dependency>
      <groupId>com.badlogicgames.box2dlights</groupId>
      <artifactId>box2dlights</artifactId>
      <version>1.5</version>
    </dependency>
    
If you use Gradle, add the following dependency to your build.gradle file, in the dependencies block of the core project:

     implementation "com.badlogicgames.box2dlights:box2dlights:1.5"

## Maintenance Note
Box2dlights was moved from Google Code to GitHub to make contributing easier. The libgdx team will happily merge pull requests but will not fix bugs or ensure compatibility with the latest libgdx version.

