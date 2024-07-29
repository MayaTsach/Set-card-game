# Set card game

## Overview
The project imlements a simple version of the game “Set” using Java Threads and Synchronization.

## Project Structure

The project includes several components:

- **Cards & Features**: Represented by integers with four features (color, number, shape, shading).
- **Table**: A grid structure to place cards and track tokens.
- **Players**: Threads that simulate human and non-human players.
- **Dealer**: The main thread managing the game flow.
- **GUI & Keyboard Input**: Pre-implemented graphical interface and input handling.

## Classes and Their Responsibilities

### Cards & Features

- Represented as integers (0 to 80).
- Each card has four features, each with three possible values.

### Table

- Holds placed cards in a 3x4 grid.
- Tracks which player placed tokens on which cards.

### Players

- Each player is represented by a separate thread.
- Players place and remove tokens on the table.
- Non-human players simulate key presses using threads.

### Dealer

- The main thread managing the game.
- Deals, shuffles, and collects cards.
- Checks for legal sets and awards points or penalties.

### GUI & Keyboard Input

- **User Interface**: Handles the display of the game.
- **Input Manager**: Manages keyboard input.
- **Window Manager**: Handles window events, including termination of threads.

## Program Flow

1. The program initializes all components and starts the dealer thread.
2. Players place tokens on cards to form legal sets.
3. The dealer checks the sets, updates the table, and awards points or penalties.
4. The game continues until no legal sets are available.

## Actions and Mechanics

### Placing Tokens

- Players place tokens on cards using designated keys.
- Once a player places the third token, the dealer checks if the set is legal.

### Legal Set

- A set of three cards where each feature is either all the same or all different.

### Penalties and Points

- Legal sets are rewarded with points.
- Illegal sets result in penalties, freezing the player for a time period.

### Dealer's Responsibilities

- Creating and running player threads.
- Dealing and shuffling cards.
- Managing the countdown timer and checking for legal sets.
- Awarding points and penalizing players.
