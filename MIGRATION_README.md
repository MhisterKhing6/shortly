# Assignment Data Migration - README

## Overview

This migration script converts old assignment data from the legacy format to the new consolidated format. The script reads three data files and creates a unified assignment structure with rider information and parcel details.

## Input Files

1. **assignment.txt** - Old assignment records (184 assignments)
2. **parcels.txt** - Parcel data (302 parcels)
3. **users.txt** - User data including riders (20 users, 6 riders)

## Output File

**migrated_assignments_final.json** - Consolidated assignment data in new format

## Migration Results

- **Total Assignments Created:** 49
- **Total Parcels Migrated:** 170
- **Total Amount:** GHS 3,809.96
- **Assignments by Status:**
  - DELIVERED: 35
  - ASSIGNED: 7
  - COMPLETED: 4
  - CANCELLED: 3

## Assignment Distribution by Rider

| Rider Name | Phone Number | Assignments |
|------------|--------------|-------------|
| John | +233535190368 | 39 |
| Kingsley New Rider | +233209220935 | 7 |
| Godfred | +233257019015 | 3 |

## Data Structure

### New Assignment Format

```json
{
  "assignmentId": "string",
  "riderInfo": {
    "riderId": "string",
    "riderName": "string",
    "riderPhoneNumber": "string"
  },
  "parcels": [
    {
      "parcelId": "string",
      "parcelDescription": "string",
      "receiverName": "string",
      "receiverPhoneNumber": "string",
      "receiverAddress": "string",
      "senderName": "string",
      "senderPhoneNumber": "string",
      "parcelAmount": 0.0,
      "payed": false,
      "cancelled": false
    }
  ],
  "amount": 0.0,
  "status": "string",
  "officeId": "string",
  "assignedAt": 0,
  "acceptedAt": 0,
  "completedAt": 0,
  "confirmationCode": "string",
  "payed": false,
  "payementMethod": null,
  "cancelationReason": null,
  "createdAt": 0,
  "updatedAt": 0
}
```

## Migration Logic

### 1. Grouping Strategy

Assignments are grouped by:
- **assignedAt timestamp** - When the assignment was created
- **officeId** - The office where the assignment originated

This grouping ensures that multiple old assignments created at the same time for the same office are consolidated into a single new assignment.

### 2. Rider Matching

The script uses a sophisticated rider matching strategy:

1. **Driver-to-Rider Mapping**: Creates a mapping from driver phone numbers (found in parcels) to actual rider users based on office affinity
2. **Office-Based Assignment**: If no direct match is found, assigns riders based on their office
3. **Fallback Assignment**: Uses a default rider if no suitable match is found

### 3. Parcel Selection

Parcels are matched to assignments based on:
- Same **officeId**
- **parcelAssigned** = true
- **delivered** status matches assignment status:
  - Undelivered parcels for PENDING/ASSIGNED/ACCEPTED assignments
  - Delivered parcels for DELIVERED/COMPLETED assignments

### 4. Amount Calculation

The total amount for each assignment is calculated as the sum of all `deliveryCost` values from the parcels.

## Key Features

1. **Consolidation**: Multiple old assignments with the same timestamp are merged into one
2. **Rider Mapping**: Intelligent matching of legacy driver phone numbers to current riders
3. **Parcel Aggregation**: All parcels for a given assignment are grouped together
4. **Status Preservation**: Original assignment status is maintained
5. **Amount Calculation**: Automatic calculation of total delivery costs

## Usage

To run the migration script:

```bash
python3 migrate_assignments.py
```

The script will:
1. Load all three input files
2. Create driver-to-rider mappings
3. Group assignments by timestamp and office
4. Match parcels to assignments
5. Generate consolidated assignment records
6. Save output to `migrated_assignments_final.json`

## Notes and Warnings

### Assignments with No Parcels

8 assignments were created with no parcels. These are typically:
- Assignments with null officeId where parcels couldn't be matched
- Completed/cancelled assignments where parcels were already delivered or removed

### Driver Phone Number Mapping

The script detected 11 unique driver phone numbers in the parcel data:
- +233531656697 (100 parcels) → John
- +233553119294 (44 parcels) → John
- +233598338777 (29 parcels) → Godfred
- +233505723170 (25 parcels) → John
- +36168191217457 (13 parcels) → Kingsley New Rider
- Others assigned to available riders

### Data Quality

- Some parcels have missing sender information (null senderName, empty senderPhoneNumber)
- Some receiver phone numbers may not be in standard format
- All migrated parcels default to `payed: false` and `cancelled: false`

## Verification

To verify the migration results:

1. **Check total parcels**: 170 parcels should be distributed across 49 assignments
2. **Check total amount**: Should sum to GHS 3,809.96
3. **Check rider distribution**: John should have most assignments (39)
4. **Check status distribution**: Most should be DELIVERED (35)

## Next Steps

After migration:

1. **Review** the migrated data in `migrated_assignments_final.json`
2. **Validate** that rider assignments are correct
3. **Import** the data into your database
4. **Update** any references to the old assignment format
5. **Test** the application with the new data structure

## Technical Details

- **Language**: Python 3
- **Dependencies**: Standard library only (json, collections)
- **Runtime**: ~1 second for 184 assignments and 302 parcels
- **Memory**: Loads all data in memory for processing

## File Locations

```
/home/kingsley-botchway/Desktop/shortlyapplication/shortly/
├── assignment.txt (input)
├── parcels.txt (input)
├── users.txt (input)
├── migrate_assignments.py (script)
├── migrated_assignments_final.json (output)
└── MIGRATION_README.md (this file)
```

## Support

If you encounter issues or need to modify the migration logic, the script is well-documented with comments explaining each function and step.
