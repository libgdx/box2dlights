# Box2DLights

[![GitHub Actions Build Status](https://img.shields.io/github/actions/workflow/status/libgdx/box2dlights/main.yml?branch=master&label=GitHub%20Actions)](https://github.com/libgdx/box2dlights/actions?query=workflow%3A%22Build+and+deploy%22)

[![Latest Version](https://img.shields.io/nexus/r/com.badlogicgames.box2dlights/box2dlights?nexusVersion=2&server=https%3A%2F%2Foss.sonatype.org&label=Version)](https://search.maven.org/artifact/com.badlogicgames.box2dlights/box2dlights)
[![Snapshots](https://img.shields.io/nexus/s/com.badlogicgames.box2dlights/box2dlights?server=https%3A%2F%2Foss.sonatype.org&label=Snapshots)](https://oss.sonatype.org/#nexus-search;gav~com.badlogicgames.box2dlights~box2dlights~~~~kw,versionexpand)

[![screenshot](http://img.youtube.com/vi/lfT8ajGbzk0/0.jpg)](http://www.youtube.com/watch?v=lfT8ajGbzk0)

Kalle Hameleinen's Box2DLights is a 2D lighting framework that uses [box2d](http://box2d.org/) for raycasting and OpenGL ES 2.0 for rendering. This library is intended to be used with [libGDX](http://libgdx.com).

Try Kalle's game [Boxtrix](https://market.android.com/details?id=boxtrix.android) to see the library in action.

## Features

 * Arbitrary number of lights
 * Gaussian blurred light maps
 * Point light
 * Cone Light
 * Directional Light
 * Chain Light [New in 1.3]
 * Shadows
 * Dynamic/static/X-ray light
 * Culling
 * Colored ambient light
 * Gamma-corrected colors
 * Handler class to do all the work
 * Query method for testing if a point is inside a light/shadow

This library offers an easy way to add soft dynamic 2d lights to your physics-based game. Rendering uses libGDX, but it
would be easy to port this to other frameworks or pure OpenGl too.

## Usage

Check out the [Wiki](https://github.com/libgdx/box2dlights/wiki) for usage information.

If you use Gradle, add the following dependency to your build.gradle file, in the last dependencies block of core/build.gradle:

     implementation 'com.github.libgdx:box2dlights:76536bb895'

That dependency uses JitPack to depend on the only commit in the 1.6 version, which makes box2dlights compatible with
libGDX 1.14.0 . If you use a earlier libGDX version, you can depend on the stable release 1.5 from Maven Central:

     implementation "com.badlogicgames.box2dlights:box2dlights:1.5"

If you use Maven, the JitPack 1.6 dependency uses:

	<dependency>
	    <groupId>com.github.libgdx</groupId>
	    <artifactId>box2dlights</artifactId>
	    <version>76536bb895</version>
	</dependency>

While the stable release for libGDX 1.13.5 and earlier uses: 

    <dependency>
      <groupId>com.badlogicgames.box2dlights</groupId>
      <artifactId>box2dlights</artifactId>
      <version>1.5</version>
    </dependency>

Information for other build tools with JitPack is [available here](https://jitpack.io/#libgdx/box2dlights/76536bb895).

If the given JitPack dependency isn't compatible with libGDX 1.14.2 or newer, you can change `76536bb895` to
`fb5cd9f8f5` to use version 1.7, which only updates libGDX to 1.14.2 and fixes some tests. The tests shouldn't apply to
JitPack's build at all, so version 1.6 (with commit hash `76536bb895`) *should* still be compatible with the latest
libGDX. If it isn't for any reason, version 1.7 (with commit hash `fb5cd9f8f5`) has been tested with libGDX 1.14.2 as
the only libGDX version present, and that works.

The stable release JARs were at one point hosted on the badlogicgames site, but it's been offline for some time. Using
Gradle or another similar tool is encouraged, but if you need to download JARs for any reason, you still can download
individual JARs manually from Maven Central or even from JitPack.
[Here's the JAR that is compatible with libGDX 1.14.0](https://jitpack.io/com/github/libgdx/box2dlights/76536bb895/box2dlights-76536bb895.jar),
for example, from JitPack directly.

## Maintenance Note
Box2dlights was moved from Google Code to GitHub to make contributing easier.
The libGDX team will happily merge pull requests but will not fix bugs or ensure compatibility with the latest libGDX version.

... Well, @tommyettinger seems to be ensuring compatibility with the latest libGDX version.

