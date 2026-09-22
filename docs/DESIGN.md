# KasirKita POS Design System

## Product Context

KasirKita is a mobile POS application for small businesses.

Primary users:
- OWNER
- ADMIN
- CASHIER

The application prioritizes:
- speed of transaction
- clarity
- minimal mistakes
- offline capability
- simple operation for non-technical users


# Design Principles

## 1. Function over Decoration

Avoid:
- unnecessary animations
- decorative cards
- excessive gradients
- dashboard-like layouts

Prioritize:
- important information
- clear actions
- fast interaction


## 2. POS Workflow First

Every screen should answer:

"What does the user need to do next?"

Examples:

Cashier:
- find product
- add cart
- checkout

Owner:
- manage product
- check sales
- manage stock


# UI Style

## Layout

Prefer:
- clean vertical layout
- large touch targets
- clear hierarchy

Avoid:
- crowded tables
- tiny buttons
- hidden actions


## Components

Use:

Primary action:
- filled button

Secondary action:
- outlined button

Danger:
- red confirmation action


# Product Management UI

Target users:
OWNER / ADMIN


Product list:

Show:
- product name
- SKU
- selling price
- stock mode


Example:

Kopi Susu

Rp16.000

SKU: KOPISUSU002

Stock:
Tidak dikelola


Actions:
- Edit


# Forms

Product form fields:

Required:
- Name
- SKU
- Price

Optional:
- Category
- Cost
- Minimum stock


Stock toggle:

ON:
Stock is managed

OFF:
Product ignores inventory


# Error Handling

Never show technical errors.

Bad:

"HTTP 409"

Good:

"SKU sudah digunakan"


# Mobile Rules

Minimum touch target:
48dp

Avoid:
- tiny icons without labels
- hidden gestures
- complex navigation


# Future Screens

Apply the same style to:

- Product Management
- Stock Management
- Customer
- Reports
- Settings