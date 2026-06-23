# Parcel Tracking Frontend — Implementation Prompt

## Overview

Build a parcel tracking UI for a logistics/courier platform. The backend is already built with role-based API endpoints. The frontend must handle QR code scanning, public tracking, and role-aware dashboard views.

## Backend API (Base URL: `{BACKEND_HOST}/shortly`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api-tracking/{parcelId}/track` | None | Public tracking view — returns limited parcel info |
| `GET` | `/api-tracking/{parcelId}` | JWT Bearer | Role-aware view — returns different data based on user role |
| `PATCH` | `/api-tracking/{parcelId}/status` | JWT (RIDER, ADMIN, MANAGER) | Update parcel status |
| `PATCH` | `/api-tracking/{parcelId}/assign` | JWT (FRONTDESK, ADMIN, MANAGER) | Assign rider to parcel |

Authentication uses JWT tokens via `Authorization: Bearer <token>` header.

---

## Response Shapes by Role

### Public (no auth) — `GET /api-tracking/{parcelId}/track`

```json
{
  "parcelId": "string",
  "parcelStatus": "RECEIVED | PENDING | DELIVERD | COLLECTED | FAILED | REVERSED",
  "typeofParcel": "PARCEL | ONLINE | PICKUP | TRANSFER",
  "isDelivered": false,
  "isParcelAssigned": true,
  "fromOfficeName": "Accra Central",
  "toOfficeName": "Kumasi Hub",
  "createdAt": 1719100000000,
  "updatedAt": 1719200000000
}
```

### Customer / Vendor — `GET /api-tracking/{parcelId}`

```json
{
  "parcelId": "string",
  "parcelDescription": "string",
  "parcelStatus": "RECEIVED",
  "typeofParcel": "PARCEL",
  "isDelivered": false,
  "isParcelAssigned": true,
  "senderName": "K***y",
  "receiverName": "John Doe",
  "fromOfficeName": "Accra Central",
  "toOfficeName": "Kumasi Hub",
  "createdAt": 1719100000000,
  "updatedAt": 1719200000000,
  "timeline": [
    { "status": "RECEIVED", "description": "Parcel received", "timestamp": 1719100000000 },
    { "status": "ASSIGNED", "description": "Assigned for delivery", "timestamp": 1719150000000 },
    { "status": "PICKED_UP", "description": "Picked up by rider", "timestamp": 1719180000000 }
  ]
}
```

### Rider — `GET /api-tracking/{parcelId}`

```json
{
  "parcelId": "string",
  "parcelDescription": "string",
  "parcelStatus": "RECEIVED",
  "typeofParcel": "PICKUP",
  "isDelivered": false,
  "isPOD": true,
  "isFragile": false,
  "homeDelivery": true,
  "receiverName": "John Doe",
  "receiverAddress": "123 Main St",
  "recieverPhoneNumber": "0241234567",
  "alternativePhoneNumber": "0209876543",
  "pickupAddress": "456 Pickup Ave",
  "pickupContactName": "Jane",
  "pickupContactPhoneNumber": "0551234567",
  "pickupInstructions": "Ring doorbell twice",
  "deliveryAddress": "789 Delivery Rd",
  "deliveryContactName": "John",
  "deliveryContactPhoneNumber": "0241234567",
  "specialInstructions": "Handle with care",
  "deliveryCost": 25.0,
  "pickUpCost": 10.0,
  "createdAt": 1719100000000,
  "updatedAt": 1719200000000
}
```

### Frontdesk — `GET /api-tracking/{parcelId}`

Includes sender/receiver full details, shelf info, driver assignment, costs, payment method.

### Admin / Manager / CallCenter — `GET /api-tracking/{parcelId}`

Returns all parcel fields including vendor info, call center data, images, reconciliation flags, etc.

---

## Request Bodies

### Update Status — `PATCH /api-tracking/{parcelId}/status`

```json
{
  "parcelStatus": "DELIVERD",
  "notes": "Left at reception"
}
```

Valid statuses: `RECEIVED`, `PENDING`, `DELIVERD`, `COLLECTED`, `FAILED`, `REVERSED`

> **Important:** `DELIVERD` is intentionally misspelled — match the backend exactly.

### Assign Driver — `PATCH /api-tracking/{parcelId}/assign`

```json
{
  "riderId": "rider-user-id-here"
}
```

---

## Frontend Routing & Behavior

### Single QR entry route: `/p/{parcelId}`

When a user scans a QR code, the encoded URL is `https://yourdomain.com/p/{parcelId}`.

### Route logic

1. On `/p/{parcelId}` load:
   - Check if user has a valid JWT token stored (localStorage/cookie)
   - **If NOT authenticated** — call `GET /api-tracking/{parcelId}/track` (public endpoint) — render public tracking page
   - **If authenticated** — call `GET /api-tracking/{parcelId}` with JWT header — render role-based view

2. The frontend does NOT decide the role — the backend returns the appropriate response shape based on the JWT. The frontend detects which view to render based on the response fields:
   - Has `timeline` array — Customer/Vendor view
   - Has `pickupInstructions` but no `senderPhoneNumber` — Rider view
   - Has `shelfName` and `driverId` but no `vendorId` — Frontdesk view
   - Has `callOutCome` or `vendorId` — Admin view
   - Alternatively, decode the JWT client-side to read the `role` claim and select the UI component

3. Additional route for direct tracking: `/track/{parcelId}` — always shows the public view (no auth check)

---

## QR Code Generation

- Generate QR codes on the frontend using a library (e.g., `qrcode.react`, `qrcode`, or `vue-qrcode`)
- QR content is simply: `https://yourdomain.com/p/{parcelId}`
- Show a "Generate QR" or "Print QR" button wherever parcels are listed (frontdesk, admin views)
- QR code should be downloadable/printable as PNG
- Do NOT embed any sensitive data in the QR — only the URL

---

## UI Components by Role

### Public Tracking Page (unauthenticated)

- Clean, minimal layout
- Show parcel status badge (color-coded: green=delivered, yellow=pending, red=failed)
- Show origin to destination offices
- Show timestamps (created, last updated)
- "Login for more details" link
- No sensitive data shown

### Customer View

- Status timeline/stepper component (vertical or horizontal)
- Each timeline event shows status icon, description, and timestamp
- Sender name is already masked by backend — display as-is
- Read-only — no action buttons

### Rider View

- Card layout with delivery instructions prominently displayed
- Receiver contact info with click-to-call phone links
- Pickup and delivery addresses (link to maps if possible)
- Special instructions highlighted (especially for fragile items)
- Status update buttons: mark as `PICKED_UP`, `DELIVERD`, `FAILED`
  - Each button calls `PATCH /api-tracking/{parcelId}/status`
- Optional notes input when updating status

### Frontdesk View

- Full sender and receiver details
- Shelf location display
- Driver assignment section:
  - If unassigned: show rider picker/dropdown + "Assign" button — calls `PATCH /api-tracking/{parcelId}/assign`
  - If assigned: show driver name, phone, and reassign option
- Cost breakdown (delivery, inbound, storage)
- Payment method and status

### Admin View

- Full detail view with all fields organized in sections/tabs:
  - Basic Info, Sender/Receiver, Driver/Rider, Costs & Payment, Pickup/Delivery, Vendor, Call Center, Images
- Status update capability (same as rider)
- Driver assignment capability (same as frontdesk)
- Image gallery for parcel photos
- Audit information

---

## Error Handling

- `401` from authenticated endpoint — redirect to login page, preserve return URL (`/p/{parcelId}`)
- `403` from status/assign endpoints — show "You don't have permission" toast
- `404` / parcel not found (backend returns `409` with "Parcel not found") — show "Parcel not found" page
- Network errors — show retry option

---

## User Roles Reference

| Role | Can View | Can Update Status | Can Assign Driver |
|---|---|---|---|
| Public (no auth) | Public tracking only | No | No |
| CUSTOMER | Customer view + timeline | No | No |
| VENDOR | Customer view + timeline | No | No |
| RIDER | Rider delivery view | Yes | No |
| FRONTDESK | Frontdesk intake view | No | Yes |
| ADMIN | Full admin view | Yes | Yes |
| MANAGER | Full admin view | Yes | Yes |
| CALLCENTER | Full admin view | No | No |
