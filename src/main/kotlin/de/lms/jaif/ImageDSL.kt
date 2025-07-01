package de.lms.jaif

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage

public infix fun Int.by(y: Int): Dimension = Dimension(this, y)

public fun Dimension.asRectangle(): Rectangle = Rectangle(0, 0, width, height)
public fun Dimension.asRectangle(offset: Point): Rectangle = Rectangle(offset.x, offset.y, width, height)
public fun Dimension.asRectangle(offsetX: Int, offsetY: Int): Rectangle = Rectangle(offsetX, offsetY, width, height)

public val BufferedImage.size: Dimension get() = width by height
public fun BufferedImage.bounds(): Rectangle = Rectangle(0, 0, width, height)
public fun BufferedImage.bounds(offset: Point): Rectangle = Rectangle(offset.x, offset.y, width, height)
public fun BufferedImage.bounds(offsetX: Int, offsetY: Int): Rectangle = Rectangle(offsetX, offsetY, width, height)
